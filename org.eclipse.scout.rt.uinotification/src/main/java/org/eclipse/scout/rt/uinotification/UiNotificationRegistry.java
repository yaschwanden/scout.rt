/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.scout.rt.uinotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.eclipse.scout.rt.api.data.uinotification.TopicDo;
import org.eclipse.scout.rt.api.data.uinotification.UiNotificationDo;
import org.eclipse.scout.rt.dataobject.IDoEntity;
import org.eclipse.scout.rt.platform.ApplicationScoped;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.util.Assertions;
import org.eclipse.scout.rt.platform.util.event.FastListenerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UiNotificationRegistry {
  private final AtomicInteger m_sequence = new AtomicInteger(1);
  private final ReadWriteLock m_lock = new ReentrantReadWriteLock();
  private final Map<NotificationBucket, List<UiNotificationDo>> m_notifications = new HashMap<>();
  private final Map<NotificationBucket, FastListenerList<UiNotificationListener>> m_listeners = new HashMap<>();

  private static final Logger LOG = LoggerFactory.getLogger(UiNotificationRegistry.class);

  public CompletableFuture<List<UiNotificationDo>> getAllOrWait(List<TopicDo> topics, String user, boolean wait) {
    List<UiNotificationDo> notifications = getAll(topics, user);
    if (!notifications.isEmpty() || !wait) {
      LOG.info("Returning {} notifications for topics {} and user {} without waiting.", notifications.size(), topics, user);
      return CompletableFuture.completedFuture(notifications);
    }
    CompletableFuture<List<UiNotificationDo>> future = new CompletableFuture<>();
    final UiNotificationListener listener = event -> {
      LOG.info("New notifications received for topics {} and user {}.", topics, user);
      future.complete(getAll(topics, user));
    };
    List<String> topicNames = topics.stream().map(topic -> topic.getName()).collect(Collectors.toList());
    addListeners(topicNames, user, listener);
    LOG.info("Waiting for new notifications for topics {} and user {}.", topics, user);
    return future.thenApply(uiNotificationDos -> {
      // TODO CGU is this called when future is cancelled?
      removeListeners(topicNames, user, listener);
      return uiNotificationDos;
    });
  }

  public List<UiNotificationDo> getAll(List<TopicDo> topics, String user) {
    List<UiNotificationDo> notifications = get(topics, null);

    // Get user specific notifications
    if (user != null) {
      notifications.addAll(get(topics, user));
    }
    return notifications;
  }

  protected List<UiNotificationDo> get(List<TopicDo> topics, String user) {
    List<UiNotificationDo> notifications = new ArrayList<>();
    for (TopicDo topic : topics) {
      notifications.addAll(get(createBucket(topic.getName(), user), topic.getLastNotificationId()));
    }
    return notifications;
  }

  protected NotificationBucket createBucket(String topic, String user) {
    NotificationBucket bucket = new NotificationBucket();
    bucket.setTopic(topic);
    bucket.setUser(user);
    return bucket;
  }

  public List<UiNotificationDo> get(NotificationBucket bucket, Integer lastNotificationId) {
    m_lock.readLock().lock();
    try {
      List<UiNotificationDo> notifications = m_notifications.get(bucket);
      if (lastNotificationId == null) {
        Integer id;
        if (notifications == null || notifications.isEmpty()) {
          // Next attempt will get all notifications
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
            .withTopic(bucket.getTopic())
            .withSubscriptionStart(true));
      }
      if (notifications == null) {
        return new ArrayList<>();
      }
      return notifications.stream()
          .filter(notification -> notification.getId() > lastNotificationId)
          .collect(Collectors.toList());
    }
    finally {
      m_lock.readLock().unlock();
    }
  }

  public void put(IDoEntity message, String topicName) {
    put(message, topicName, null);
  }

  public void put(IDoEntity message, String topic, String userId) {
    Assertions.assertNotNull(message, "Message must not be null");
    Assertions.assertNotNull(topic, "Topic must not be null");
    UiNotificationDo notification = BEANS.get(UiNotificationDo.class)
        .withId(m_sequence.getAndIncrement())
        .withTopic(topic)
        .withMessage(message);
    NotificationBucket bucket = new NotificationBucket();
    bucket.setTopic(topic);
    bucket.setUser(userId);

    m_lock.writeLock().lock();
    try {
      List<UiNotificationDo> uiNotifications = m_notifications.computeIfAbsent(bucket, key -> new ArrayList<>());
      uiNotifications.add(notification);
      LOG.info("Added new ui notification {} for topic {}. New size: {}", notification, topic, uiNotifications.size());

      // TODO CGU message needs to be published over cluster, crm and studio use different service -> use studio service only? make abstraction?
      // TODO CGU keep inside lock? It ensures no more notifications can be added during future completion, but it blocks longer. Maybe it would be sufficient to check if response is done in ui notification resource
      triggerEvent(bucket, notification);
    }
    finally {
      m_lock.writeLock().unlock();
    }
  }

  public void addListener(NotificationBucket topic, UiNotificationListener listener) {
    FastListenerList<UiNotificationListener> listeners = m_listeners.computeIfAbsent(topic, k -> new FastListenerList<>());
    listeners.add(listener); // TODO CGU weak true?
  }

  public void removeListener(NotificationBucket topic, UiNotificationListener listener) {
    FastListenerList<UiNotificationListener> listeners = m_listeners.get(topic);
    if (listeners == null) {
      return;
    }
    listeners.remove(listener);
    if (listeners.isEmpty()) {
      m_listeners.remove(topic);
    }
  }

  public void addListeners(List<String> topics, String user, UiNotificationListener listener) {
    for (String topicName : topics) {
      addListener(createBucket(topicName, null), listener);
    }
    if (user != null) {
      for (String topicName : topics) {
        addListener(createBucket(topicName, user), listener);
      }
    }
  }

  public void removeListeners(List<String> topics, String user, UiNotificationListener listener) {
    for (String topicName : topics) {
      removeListener(createBucket(topicName, null), listener);
    }
    if (user != null) {
      for (String topicName : topics) {
        removeListener(createBucket(topicName, user), listener);
      }
    }
  }

  protected void triggerEvent(NotificationBucket bucket, UiNotificationDo notification) {
    FastListenerList<UiNotificationListener> listeners = m_listeners.get(bucket);
    if (listeners == null) {
      return;
    }
    for (UiNotificationListener listener : listeners.list()) {
      listener.notificationAdded(new UiNotificationEvent(this, notification));
    }
  }
}
