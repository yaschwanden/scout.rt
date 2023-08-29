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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.eclipse.scout.rt.api.data.uinotification.UiNotificationDo;
import org.eclipse.scout.rt.dataobject.DoEntity;
import org.eclipse.scout.rt.platform.ApplicationScoped;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.util.ObjectUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UiNotificationRegistry {
  //  private Map<SubscriptionId, BlockingQueue<DoEntity>> m_queues = new HashMap<>();

  private final AtomicInteger m_sequence = new AtomicInteger(1);
  private final ReadWriteLock m_lock = new ReentrantReadWriteLock();
  private final Map<Topic, List<UiNotificationDo>> m_notifications = new HashMap<>();

  //  private Map<String, FastListenerList> m_topicListeners = new HashMap<>();
  //  private Map<String, FastListenerList> m_userListeners = new HashMap<>();
  //  private Map<String, FastListenerList> m_allListeners = new HashMap<>();

  private static final Logger LOG = LoggerFactory.getLogger(UiNotificationRegistry.class);
  //
  //  public SubscriptionId subscribe() {
  //    SubscriptionId id = SubscriptionId.create();
  //    m_queues.put(id, new LinkedBlockingQueue<>());
  //    LOG.info("New subscription with id {}. Total subscriptions: {} ", id, m_queues.size());
  //    return id;
  //  }
  //
  //  public void unsubscribe(SubscriptionId id) {
  //    m_queues.remove(id);
  //  }
  //
  //  public List<DoEntity> poll(SubscriptionId id, boolean longPolling) {
  //    // TODO CGU listener registrieren pro topic, user oder alle
  //    // TODO async testen mit Jersey 3? oder für den Moment mit Thread blockieren mit mutex pro liste?
  //
  //    LOG.info("Waiting for new ui notifications for subscription {} ...", id);
  //    BlockingQueue<DoEntity> queue = m_queues.get(id);
  //    DoEntity element = null;
  //    List<DoEntity> notifications = new ArrayList<>();
  //    if (longPolling) {
  //      try {
  //        Integer timeout = 60000;
  //        element = queue.poll(timeout, TimeUnit.MILLISECONDS);
  //        // TODO CGU consider session timeout?
  //      }
  //      catch (InterruptedException e) {
  //        throw new RuntimeException(e);
  //      }
  //      notifications.add(element);
  //      queue.drainTo(notifications);
  //    } else {
  //      queue.drainTo(notifications);
  //    }
  //    //    response.resume(new ClientNotificationResponse().withNotifications(notifications));
  //    queue.clear();
  //    LOG.info("Returning new ui notification: {}.", notifications);
  //    return notifications;
  //  }

  public List<UiNotificationDo> poll(List<String> topics, String user, Integer lastId) {
    List<UiNotificationDo> notifications = getAll(topics, user, lastId);
    if (!notifications.isEmpty()) {
      return notifications;
    }

    synchronized (m_notifications) {
      try {
        // TODO CGU replace with async pattern, do not block threads
        m_notifications.wait(60000);
      }
      catch (InterruptedException e) {
        // Wake up
      }
    }
    return getAll(topics, user, lastId);
  }

  public List<UiNotificationDo> getAll(List<String> topics, String user, Integer lastId) {
    List<UiNotificationDo> notifications = get(topics, null, lastId);
    if (user != null) {
      notifications.addAll(get(topics, user, lastId));
    }
    return notifications;
  }

  public List<UiNotificationDo> get(List<String> topics, String user, Integer lastId) {
    List<UiNotificationDo> notifications = new ArrayList<>();
    for (String topicName : topics) {
      Topic topic = new Topic();
      topic.setTopic(topicName);
      topic.setUser(user);
      notifications.addAll(get(topic, lastId));
    }
    LOG.info("Returning {} notifications for topics {} and user {}.", notifications.size(), topics, user);
    return notifications;
  }

  public List<UiNotificationDo> get(Topic topic, Integer lastId) {
    m_lock.readLock().lock();
    try {
      List<UiNotificationDo> notifications = m_notifications.get(topic);
      if (notifications == null) {
        return new ArrayList<>();
      }
      return notifications.stream()
          .filter(notification -> notification.getId() > ObjectUtility.nvl(lastId, -1))
          .collect(Collectors.toList());
    }
    finally {
      m_lock.readLock().unlock();
    }
  }

  public void put(DoEntity message) {
    put(message, null, null);
  }

  public void put(DoEntity message, String topicName, String userId) {
    // TODO CGU allow null topics? or create general topic?
    UiNotificationDo notification = BEANS.get(UiNotificationDo.class)
        .withId(m_sequence.getAndIncrement())
        .withTopic(topicName)
        .withMessage(message);
    Topic topic = new Topic();
    topic.setTopic(topicName);
    topic.setUser(userId);

    m_lock.writeLock().lock();
    try {
      List<UiNotificationDo> uiNotifications = m_notifications.computeIfAbsent(topic, key -> new ArrayList<>());
      uiNotifications.add(notification);

      // TODO CGU replace with async pattern
      synchronized (m_notifications) {
        m_notifications.notifyAll();
      }

      // TODO CGU message needs to be published over cluster, crm and studio use different service -> use studio service only? make abstraction?

      LOG.info("Added new ui notification {} for topic {}. New size: {}", notification, topic, uiNotifications.size());
    }
    finally {
      m_lock.writeLock().unlock();
    }
  }
}
