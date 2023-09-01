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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.eclipse.scout.rt.api.data.uinotification.UiNotificationDo;
import org.eclipse.scout.rt.dataobject.DoEntity;
import org.eclipse.scout.rt.platform.ApplicationScoped;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.util.Assertions;
import org.eclipse.scout.rt.platform.util.ObjectUtility;
import org.eclipse.scout.rt.platform.util.event.FastListenerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UiNotificationRegistry {
  private final AtomicInteger m_sequence = new AtomicInteger(1);
  private final ReadWriteLock m_lock = new ReentrantReadWriteLock();
  private final Map<Topic, List<UiNotificationDo>> m_notifications = new HashMap<>();
  private final Map<Topic, FastListenerList<UiNotificationListener>> m_listeners = new HashMap<>();

  private static final Logger LOG = LoggerFactory.getLogger(UiNotificationRegistry.class);

  public CompletableFuture<List<UiNotificationDo>> getAllOrWait(List<String> topics, String user, Integer lastId, boolean wait) {
    List<UiNotificationDo> notifications = getAll(topics, user, lastId);
    if (!notifications.isEmpty() || !wait) {
      LOG.info("Returning {} notifications for topics {} and user {} without waiting.", notifications.size(), topics, user);
      return CompletableFuture.completedFuture(notifications);
    }
    CompletableFuture<List<UiNotificationDo>> future = new CompletableFuture<>();
    final UiNotificationListener listener = event -> {
      LOG.info("New notifications received for topics {} and user {}.", topics, user);
      future.complete(getAll(topics, user, lastId));
    };
    addListeners(topics, user, listener);
    LOG.info("Waiting for new notifications for topics {} and user {}.", topics, user);
    return future.thenApply(uiNotificationDos -> {
      // TODO CGU is this called when future is cancelled?
      removeListeners(topics, user, listener);
      return uiNotificationDos;
    });
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
      notifications.addAll(get(createTopic(topicName, user), lastId));
    }
    return notifications;
  }

  protected Topic createTopic(String name, String user) {
    Topic topic = new Topic();
    topic.setTopic(name);
    topic.setUser(user);
    return topic;
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

  public void put(DoEntity message, String topicName) {
    put(message, topicName, null);
  }

  public void put(DoEntity message, String topicName, String userId) {
    Assertions.assertNotNull(message, "Message must not be null");
    Assertions.assertNotNull(topicName, "Topic must not be null");
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
      LOG.info("Added new ui notification {} for topic {}. New size: {}", notification, topic, uiNotifications.size());

      // TODO CGU message needs to be published over cluster, crm and studio use different service -> use studio service only? make abstraction?
      // TODO CGU keep inside lock? It ensures no more notifications can be added during future completion, but it blocks longer. Maybe it would be sufficient to check if response is done in ui notification resource
      triggerEvent(topic, notification);
    }
    finally {
      m_lock.writeLock().unlock();
    }
  }

  public void addListener(Topic topic, UiNotificationListener listener) {
    FastListenerList<UiNotificationListener> listeners = m_listeners.computeIfAbsent(topic, k -> new FastListenerList<>());
    listeners.add(listener); // TODO CGU weak true?
  }

  public void removeListener(Topic topic, UiNotificationListener listener) {
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
      addListener(createTopic(topicName, null), listener);
    }
    if (user != null) {
      for (String topicName : topics) {
        addListener(createTopic(topicName, user), listener);
      }
    }
  }

  public void removeListeners(List<String> topics, String user, UiNotificationListener listener) {
    for (String topicName : topics) {
      removeListener(createTopic(topicName, null), listener);
    }
    if (user != null) {
      for (String topicName : topics) {
        removeListener(createTopic(topicName, user), listener);
      }
    }
  }

  protected void triggerEvent(Topic topic, UiNotificationDo notification) {
    FastListenerList<UiNotificationListener> listeners = m_listeners.get(topic);
    if (listeners == null) {
      return;
    }
    for (UiNotificationListener listener : listeners.list()) {
      listener.notificationAdded(new UiNotificationEvent(this, notification));
    }
  }
}
