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
import org.eclipse.scout.rt.api.uinotification.UiNotificationConfigProperties.RegistryCleanupJobIntervalProperty;
import org.eclipse.scout.rt.api.uinotification.UiNotificationConfigProperties.UiNotificationExpirationTimeProperty;
import org.eclipse.scout.rt.api.uinotification.UiNotificationConfigProperties.UiNotificationWaitTimeoutProperty;
import org.eclipse.scout.rt.dataobject.IDoEntity;
import org.eclipse.scout.rt.platform.ApplicationScoped;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.config.CONFIG;
import org.eclipse.scout.rt.platform.exception.ExceptionHandler;
import org.eclipse.scout.rt.platform.holders.BooleanHolder;
import org.eclipse.scout.rt.platform.job.FixedDelayScheduleBuilder;
import org.eclipse.scout.rt.platform.job.IFuture;
import org.eclipse.scout.rt.platform.job.Jobs;
import org.eclipse.scout.rt.platform.transaction.ITransaction;
import org.eclipse.scout.rt.platform.util.Assertions;
import org.eclipse.scout.rt.platform.util.ObjectUtility;
import org.eclipse.scout.rt.platform.util.event.FastListenerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UiNotificationRegistry {
  private AtomicInteger m_sequence = new AtomicInteger(1);
  private ReadWriteLock m_lock = new ReentrantReadWriteLock();
  private Map<String, List<UiNotificationRegistryElement>> m_notifications = new HashMap<>();
  private Map<String, FastListenerList<UiNotificationListener>> m_listeners = new HashMap<>();
  private IFuture<Void> m_cleanupJob;
  private long m_cleanupJobInterval = CONFIG.getPropertyValue(RegistryCleanupJobIntervalProperty.class);
  private IUiNotificationClusterService m_clusterService;

  private static final Logger LOG = LoggerFactory.getLogger(UiNotificationRegistry.class);

  public UiNotificationRegistry() {
    m_clusterService = BEANS.opt(IUiNotificationClusterService.class);
    if (m_clusterService == null) {
      // TODO CGU scout js only without scout server dependency will get this warning. Change to info?
      LOG.warn("No implementation for IUiNotificationClusterService found. UI notifications won't be delivered to other cluster nodes.");
    }
  }

  public CompletableFuture<List<UiNotificationDo>> getOrWait(List<TopicDo> topics, String user) {
    return getOrWait(topics, user, CONFIG.getPropertyValue(UiNotificationWaitTimeoutProperty.class));
  }

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
          .filter(elem -> {
            // If element contains a user it must match the given user
            if (elem.getMessage().getUser() != null) {
              return elem.getMessage().getUser().equals(user);
            }
            return true;
          })
          .map(elem -> elem.getMessage().getNotification());

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
    put(message, topic, null, null);
  }

  public void put(IDoEntity message, String topic, UiNotificationPutOptions options) {
    put(message, topic, null, options);
  }

  public void put(IDoEntity message, String topic, String userId) {
    put(message, topic, userId, null);
  }

  /**
   * Puts a message into the registry for a specific topic and user.
   *
   * @param message The message part of the {@link UiNotificationDo}.
   * @param topic A notification must be assigned to a topic.
   * @param userId If specified, only the user with this id will get the notification.
   */
  public void put(IDoEntity message, String topic, String userId, UiNotificationPutOptions options) {
    Assertions.assertNotNull(message, "Message must not be null");
    Assertions.assertNotNull(topic, "Topic must not be null");
    if (options == null) {
      options = new UiNotificationPutOptions();
    }

    UiNotificationDo notification = BEANS.get(UiNotificationDo.class)
        .withId(m_sequence.getAndIncrement())
        .withTopic(topic)
        .withMessage(message);

    UiNotificationMessageDo metaMessage = BEANS.get(UiNotificationMessageDo.class)
        .withNotification(notification)
        .withUser(userId)
        .withTimeout(ObjectUtility.nvl(options.getTimeout(), CONFIG.getPropertyValue(UiNotificationExpirationTimeProperty.class)));

    if (ObjectUtility.nvl(options.getTransactional(), true)) {
      putTransactional(metaMessage);
    } else {
      putInternal(metaMessage);
    }
  }

  protected void putTransactional(UiNotificationMessageDo message) {
    ITransaction transaction = Assertions.assertNotNull(ITransaction.CURRENT.get(), "No transaction found on current calling context to register transactional ui notification {}", message);
    try {
      UiNotificationTransactionMember txMember = (UiNotificationTransactionMember) transaction.getMember(UiNotificationTransactionMember.TRANSACTION_MEMBER_ID);
      if (txMember == null) {
        txMember = new UiNotificationTransactionMember(this);
        transaction.registerMember(txMember);
      }
      txMember.addNotification(message);
    }
    catch (RuntimeException e) {
      LOG.warn("Could not register transaction member. The notification will be processed immediately", e);
      putInternal(message);
    }
  }

  protected void publishOverCluster(UiNotificationMessageDo message) {
    if (m_clusterService == null) {
      return;
    }
    m_clusterService.publish(message);
    LOG.info("Published ui notification with id {} for topic {} and user {} to other cluster nodes.", message.getNotification().getId(), message.getNotification().getTopic(), message.getUser());
  }

  public void handleClusterNotification(UiNotificationMessageDo message) {
    LOG.info("Received ui notification with id {} from another cluster node for topic {} and user {}.", message.getNotification().getId(), message.getNotification().getTopic(), message.getUser());
    putInternal(message, false);
  }

  protected void putInternal(UiNotificationMessageDo message) {
    putInternal(message, true);
  }

  protected void putInternal(UiNotificationMessageDo message, boolean publishOverCluster) {
    UiNotificationRegistryElement element = new UiNotificationRegistryElement();
    UiNotificationDo notification = message.getNotification();
    element.setMessage(message);
    element.setValidUntil(new Date(System.currentTimeMillis() + message.getTimeout()));

    String topic = notification.getTopic();
    m_lock.writeLock().lock();
    try {
      List<UiNotificationRegistryElement> uiNotifications = getNotifications().computeIfAbsent(topic, key -> new ArrayList<>());
      uiNotifications.add(element);
      LOG.info("Added new ui notification {} for topic {}. New size: {}", notification, topic, uiNotifications.size());
      // TODO CGU keep inside lock? It ensures no more notifications can be added during future completion, but it blocks longer. Maybe it would be sufficient to check if response is done in ui notification resource
      // Example put, put -> second put completes future again and throws exception?
      triggerEvent(topic, notification);
    }
    finally {
      m_lock.writeLock().unlock();
    }
    startCleanupJob();
    if (publishOverCluster) {
      publishOverCluster(message);
    }
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

  protected final Map<String, List<UiNotificationRegistryElement>> getNotifications() {
    return m_notifications;
  }

  /**
   * Removes all expires ui notifications.
   *
   * @see UiNotificationRegistryElement#getValidUntil()
   */
  public void cleanup() {
    m_lock.writeLock().lock();
    try {
      if (getNotifications().isEmpty()) {
        return;
      }
      LOG.debug("Cleaning up expired ui notifications. Topic count: {}.", getNotifications().size());

      long now = new Date().getTime();
      for (Entry<String, List<UiNotificationRegistryElement>> entry : getNotifications().entrySet()) {
        List<UiNotificationRegistryElement> notifications = entry.getValue();
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
    if (m_cleanupJob != null || getCleanupJobInterval() == 0) {
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
            .withSchedule(FixedDelayScheduleBuilder.repeatForever(getCleanupJobInterval(), TimeUnit.SECONDS))));
  }

  /**
   * Configures how often the cleanup job should run.
   * <p>
   * The property needs to be set before the cleanup job is scheduled, which is, before the first notification is put.
   *
   * @param cleanupJobInterval The interval in seconds between job runs. 0 to disable the job.
   */
  public void setCleanupJobInterval(long cleanupJobInterval) {
    m_cleanupJobInterval = cleanupJobInterval;
  }

  public long getCleanupJobInterval() {
    return m_cleanupJobInterval;
  }
}
