/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.scout.rt.api.uinotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.scout.rt.api.data.uinotification.TopicDo;
import org.eclipse.scout.rt.api.data.uinotification.UiNotificationDo;
import org.eclipse.scout.rt.dataobject.IDoEntity;
import org.eclipse.scout.rt.platform.ApplicationScoped;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.exception.ExceptionHandler;
import org.eclipse.scout.rt.platform.holders.BooleanHolder;
import org.eclipse.scout.rt.platform.job.FixedDelayScheduleBuilder;
import org.eclipse.scout.rt.platform.job.IFuture;
import org.eclipse.scout.rt.platform.job.Jobs;
import org.eclipse.scout.rt.platform.util.Assertions;
import org.eclipse.scout.rt.platform.util.event.FastListenerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UiNotificationRegistry {
  private AtomicInteger m_sequence = new AtomicInteger(1);
  private ReadWriteLock m_lock = new ReentrantReadWriteLock();
  private Map<String, List<UiNotificationElement>> m_notifications = new HashMap<>();
  private Map<String, FastListenerList<UiNotificationListener>> m_listeners = new HashMap<>();
  private IFuture<Void> m_cleanupJob;

  private static final Logger LOG = LoggerFactory.getLogger(UiNotificationRegistry.class);

  public CompletableFuture<List<UiNotificationDo>> getOrWait(List<TopicDo> topics, String user, long timeout) {
    List<UiNotificationDo> notifications = get(topics, user);
    if (!notifications.isEmpty() || timeout <= 0) {
      LOG.info("Returning {} notifications for topics {} and user {} without waiting.", notifications.size(), topics, user);
      return CompletableFuture.completedFuture(notifications);
    }

    CompletableFuture<List<UiNotificationDo>> future = new CompletableFuture<>();
    final UiNotificationListener listener = event -> {
      List<UiNotificationDo> newNotifications = get(topics, user);
      if (!newNotifications.isEmpty()) {
        LOG.info("New notifications received for topics {} and user {}.", topics, user);
        future.complete(newNotifications);
      }
    };
    List<String> topicNames = topics.stream().map(topic -> topic.getName()).collect(Collectors.toList());
    addListeners(topicNames, listener);

    LOG.info("Waiting for new notifications for topics {} and user {}.", topics, user);

    return future.thenApply(uiNotificationDos -> {
      removeListeners(topicNames, user, listener);
      return uiNotificationDos;
    }).orTimeout(timeout, TimeUnit.MILLISECONDS).exceptionally(throwable -> {
      removeListeners(topicNames, user, listener);
      if (throwable instanceof TimeoutException) {
        LOG.info("Timeout reached, stop waiting");
      }
      else {
        LOG.error("Exception while waiting for notifications", throwable);
      }
      return new ArrayList<>();
    });
  }

  public List<UiNotificationDo> get(List<TopicDo> topics, String user) {
    List<UiNotificationDo> notifications = new ArrayList<>();
    for (TopicDo topic : topics) {
      notifications.addAll(get(topic.getName(), user, topic.getLastNotificationId()));
    }
    return notifications;
  }

  protected List<UiNotificationDo> get(String topic, String user, Integer lastNotificationId) {
    m_lock.readLock().lock();
    try {
      Stream<UiNotificationDo> notificationStream = getNotifications().getOrDefault(topic, new ArrayList<>()).stream()
          .filter(notification -> {
            // If element contains a user it must match the given user
            if (notification.getUser() != null) {
              return notification.getUser().equals(user);
            }
            return true;
          })
          .map(elem -> elem.getNotification());

      if (lastNotificationId == null) {
        List<UiNotificationDo> notifications = notificationStream.collect(Collectors.toList());
        Integer id;
        if (notifications == null || notifications.isEmpty()) {
          // Next attempt will get all notifications for this topic
          id = -1;
        } else {
          // Next attempt will get all new since the current last one
          id = notifications.get(notifications.size() - 1).getId();
        }
        // Create a notification to mark the start of the subscription.
        // This is necessary to ensure the client receives every notification from now on even if the connection
        // temporarily drops before the first real notification can be sent.
        // During that connection drop a notification could be added that needs to be sent as soon as the connection is reestablished again.
        return Collections.singletonList(new UiNotificationDo()
            .withId(id)
            .withTopic(topic)
            .withSubscriptionStart(true));
      }

      // Find the last element and return the next ones
      // Don't use notification.get() > lastNotificationId for the extremely rare case if max value is reached and sequence starts over again
      final BooleanHolder found = new BooleanHolder(false);
      return notificationStream
          .filter(notification -> {
            if (lastNotificationId.equals(-1) || found.getValue()) {
              return true;
            }
            found.setValue(notification.getId().equals(lastNotificationId));
            // TODO CGU or just use .filter(notification -> notification.getId() > lastNotificationId)
            return false;
          })
          .collect(Collectors.toList());
    }
    finally {
      m_lock.readLock().unlock();
    }
  }

  public void put(IDoEntity message, String topic) {
    put(message, topic, null);
  }

  public void put(IDoEntity message, String topic, String userId) {
    put(message, topic, userId, TimeUnit.SECONDS.toMillis(10));
  }

  public void put(IDoEntity message, String topic, String userId, long timeout) {
    Assertions.assertNotNull(message, "Message must not be null");
    Assertions.assertNotNull(topic, "Topic must not be null");
    UiNotificationDo notification = BEANS.get(UiNotificationDo.class)
        .withId(m_sequence.getAndIncrement())
        .withTopic(topic)
        .withMessage(message);
    UiNotificationElement element = new UiNotificationElement();
    element.setNotification(notification);
    element.setUser(userId);
    element.setValidUntil(new Date(System.currentTimeMillis() + timeout));

    m_lock.writeLock().lock();
    try {
      List<UiNotificationElement> uiNotifications = getNotifications().computeIfAbsent(topic, key -> new ArrayList<>());
      uiNotifications.add(element);
      LOG.info("Added new ui notification {} for topic {}. New size: {}", notification, topic, uiNotifications.size());

      // TODO CGU message needs to be published over cluster, crm and studio use different service -> use studio service only? make abstraction?
      // TODO CGU keep inside lock? It ensures no more notifications can be added during future completion, but it blocks longer. Maybe it would be sufficient to check if response is done in ui notification resource
      triggerEvent(topic, notification);
    }
    finally {
      m_lock.writeLock().unlock();
    }
    startCleanupJob();
  }

  public void addListener(String topic, UiNotificationListener listener) {
    FastListenerList<UiNotificationListener> listeners = m_listeners.computeIfAbsent(topic, k -> new FastListenerList<>());
    listeners.add(listener); // TODO CGU weak true?
  }

  public void removeListener(String topic, UiNotificationListener listener) {
    FastListenerList<UiNotificationListener> listeners = m_listeners.get(topic);
    if (listeners == null) {
      return;
    }
    listeners.remove(listener);
    if (listeners.isEmpty()) {
      m_listeners.remove(topic);
    }
  }

  public void addListeners(List<String> topics, UiNotificationListener listener) {
    for (String topicName : topics) {
      addListener(topicName, listener);
    }
  }

  public void removeListeners(List<String> topics, String user, UiNotificationListener listener) {
    for (String topicName : topics) {
      removeListener(topicName, listener);
    }
  }

  protected void triggerEvent(String topic, UiNotificationDo notification) {
    FastListenerList<UiNotificationListener> listeners = getListeners(topic);
    if (listeners == null) {
      return;
    }
    for (UiNotificationListener listener : listeners.list()) {
      listener.notificationAdded(new UiNotificationEvent(this, notification));
    }
  }

  protected final FastListenerList<UiNotificationListener> getListeners(String topic) {
    return m_listeners.get(topic);
  }

  protected final Map<String, List<UiNotificationElement>> getNotifications() {
    return m_notifications;
  }

  /**
   * Removes all expires ui notifications.
   *
   * @see UiNotificationElement#getValidUntil()
   */
  public void cleanup() {
    m_lock.writeLock().lock();
    try {
      if (getNotifications().isEmpty()) {
        return;
      }
      LOG.debug("Cleaning up expired ui notifications. Topic count: {}.", getNotifications().size());

      long now = new Date().getTime();
      for (Entry<String, List<UiNotificationElement>> entry : getNotifications().entrySet()) {
        List<UiNotificationElement> notifications = entry.getValue();
        int oldSize = notifications.size();
        if (notifications.removeIf(elem -> elem.getValidUntil().getTime() < now)) {
          int newSize = notifications.size();
          LOG.info("Removed {} expired notifications for topic {}. New size: {}.", oldSize - newSize, entry.getKey(), newSize);
        }
      }

      // Remove topic if there are no notifications left
      getNotifications().entrySet().removeIf(entry -> entry.getValue().isEmpty());
      LOG.debug("Clean up finished. New topic count: {}.", getNotifications().size());
    }
    finally {
      m_lock.writeLock().unlock();
    }
  }

  public void startCleanupJob() {
    if (m_cleanupJob != null) {
      // Already started
      return;
    }
    LOG.info("Starting cleanup job");
    m_cleanupJob = Jobs.schedule(() -> {
      BEANS.get(UiNotificationRegistry.class).cleanup();

      m_lock.readLock().lock();
      try {
        if (getNotifications().isEmpty()) {
          m_cleanupJob.cancel(false);
          m_cleanupJob = null;
          LOG.info("Cleanup job stopped.");
        }
      }
      finally {
        m_lock.readLock().unlock();
      }
    }, Jobs.newInput()
        .withName("UI Notification registry cleanup")
        .withExceptionHandling(new ExceptionHandler() {
          @Override
          public void handle(Throwable t) {
            LOG.error("Exception while running ui notification registry cleanup job", t);
          }
        }, true)
        .withExecutionTrigger(Jobs
            .newExecutionTrigger()
            .withSchedule(FixedDelayScheduleBuilder.repeatForever(10, TimeUnit.SECONDS))));
  }
}
