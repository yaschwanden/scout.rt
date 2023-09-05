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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.scout.rt.api.data.uinotification.TopicDo;
import org.eclipse.scout.rt.api.data.uinotification.UiNotificationDo;
import org.eclipse.scout.rt.dataobject.DoEntity;
import org.eclipse.scout.rt.dataobject.IDoEntity;
import org.eclipse.scout.rt.platform.holders.BooleanHolder;
import org.eclipse.scout.rt.testing.platform.runner.PlatformTestRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(PlatformTestRunner.class)
public class UiNotificationRegistryTest {
  private static long WAIT_TIMEOUT = 60000;
  private UiNotificationRegistry m_registry;

  @Before
  public void before() {
    m_registry = new UiNotificationRegistry();
  }

  // TODO CGU disable cleanup job during tests

  @Test
  public void testGetAll() {
    IDoEntity message = createMessage();
    m_registry.put(message, "topic");

    // Returns all added notifications
    assertEquals(Arrays.asList(createNotification(message, "topic", 1)), m_registry.get(Arrays.asList(createTopic("topic", -1)), null));

    // There are no user specific notifications in the queue -> same result as ab above
    assertEquals(Arrays.asList(createNotification(message, "topic", 1)), m_registry.get(Arrays.asList(createTopic("topic", -1)), "otto"));
  }

  @Test
  public void testGetAllWrongTopic() {
    IDoEntity message = createMessage();
    m_registry.put(message, "topic");

    assertEquals(new ArrayList<>(), m_registry.get(Arrays.asList(createTopic("asdf", -1)), null));
  }

  @Test
  public void testGetAllInitial() {
    IDoEntity message = createMessage();
    m_registry.put(message, "topic");

    // Returns only subscription start notification if lastNotificationId is null
    assertEquals(Arrays.asList(createSubscriptionStartNotification("topic", 1)), m_registry.get(Arrays.asList(createTopic("topic", null)), null));

    // All notifications are already read (lastNotificationId is 1)
    assertEquals(new ArrayList<>(), m_registry.get(Arrays.asList(createTopic("topic", 1)), null));
  }

  @Test
  public void testGetAllWithLastId() {
    IDoEntity message = createMessage();
    m_registry.put(message, "topic");

    // All notifications are already read (lastNotificationId is 1)
    assertEquals(new ArrayList<>(), m_registry.get(Arrays.asList(createTopic("topic", 1)), null));

    IDoEntity newMessage = createMessage("a");
    m_registry.put(newMessage, "topic");

    IDoEntity newMessage2 = createMessage("b");
    m_registry.put(newMessage2, "topic");
    assertEquals(Arrays.asList(createNotification(newMessage, "topic", 2), createNotification(newMessage2, "topic", 3)), m_registry.get(Arrays.asList(createTopic("topic", 1)), null));

    // All notifications are already read (lastNotificationId is 3)
    assertEquals(new ArrayList<>(), m_registry.get(Arrays.asList(createTopic("topic", 3)), null));
  }

  @Test
  public void testGetAllMultipleTopicsWithLastId() {
    IDoEntity message = createMessage();
    m_registry.put(message, "topic a"); // 1

    // First call has lastNotificationId set to null -> subscribe
    List<UiNotificationDo> subscriptions = m_registry.get(Arrays.asList(createTopic("topic a", null), createTopic("topic b", null)), null);
    assertEquals(Arrays.asList(createSubscriptionStartNotification("topic a", 1), createSubscriptionStartNotification("topic b", -1)), subscriptions);

    IDoEntity newMessage = createMessage("a");
    m_registry.put(newMessage, "topic a"); // 2

    IDoEntity newMessage2 = createMessage("b");
    m_registry.put(newMessage2, "topic b"); // 3

    // topic a: lastId is 1 (id of last notification for that topic) because there was already one notification which must not be sent
    // topic b: lastId is -1 because there was no notification -> all notifications for that topic will be returned
    Integer topicALastId = subscriptions.get(0).getId();
    Integer topicBLastId = subscriptions.get(1).getId();
    List<UiNotificationDo> expected = Arrays.asList(createNotification(newMessage, "topic a", 2), createNotification(newMessage2, "topic b", 3));
    assertEquals(expected, m_registry.get(Arrays.asList(createTopic("topic a", topicALastId), createTopic("topic b", topicBLastId)), null));
  }

  @Test
  public void testGetAllWithLastIdAndUser() {
    IDoEntity message = createMessage();
    m_registry.put(message, "topic a", "otto"); // 1

    IDoEntity message2 = createMessage();
    m_registry.put(message2, "topic a", "max"); // 2

    IDoEntity message3 = createMessage();
    m_registry.put(message3, "topic a", null); // 3

    List<UiNotificationDo> subscriptions = m_registry.get(Arrays.asList(createTopic("topic a", null)), "otto");
    assertEquals(Arrays.asList(createSubscriptionStartNotification("topic a", 3)), subscriptions);

    IDoEntity newMessage = createMessage("a new");
    m_registry.put(newMessage, "topic a"); // 4

    IDoEntity newMessage2 = createMessage("a new user");
    m_registry.put(newMessage2, "topic a", "otto"); // 5

    IDoEntity newMessage3 = createMessage("a new user max");
    m_registry.put(newMessage3, "topic a", "max"); // 6

    Integer topicALastId = subscriptions.get(0).getId();
    List<UiNotificationDo> expected = Arrays.asList(createNotification(newMessage, "topic a", 4), createNotification(newMessage2, "topic a", 5));
    assertEquals(expected, m_registry.get(Arrays.asList(createTopic("topic a", topicALastId)), "otto"));
  }

  @Test
  public void testGetAllOrWait() {
    IDoEntity message = createMessage();

    BooleanHolder completed = new BooleanHolder();
    CompletableFuture<List<UiNotificationDo>> future = m_registry.getOrWait(Arrays.asList(createTopic("topic", -1)), null, WAIT_TIMEOUT);
    future.thenApply(notifications -> {
      assertEquals(Arrays.asList(createNotification(message, "topic", 1)), notifications);
      completed.setValue(true);
      return notifications;
    });

    // Waiting for notifications
    assertEquals(1, m_registry.getListeners("topic").list().size());

    m_registry.put(message, "topic");
    assertNull(m_registry.getListeners("topic"));

    assertEquals(true, completed.getValue());;
  }

  @Test
  public void testGetAllOrWaitPutBeforeWait() throws ExecutionException, InterruptedException, TimeoutException {
    // Put before listening -> getAllOrWait should return it immediately
    IDoEntity message = createMessage();
    m_registry.put(message, "topic");

    BooleanHolder completed = new BooleanHolder();
    CompletableFuture<List<UiNotificationDo>> future = m_registry.getOrWait(Arrays.asList(createTopic("topic", -1)), null, WAIT_TIMEOUT);
    future.thenApply(notifications -> {
      assertEquals(Arrays.asList(createNotification(message, "topic", 1)), notifications);
      completed.setValue(true);
      return notifications;
    });
    // Not waiting for notifications
    assertNull(m_registry.getListeners("topic"));

    // Wait for future to complete
    future.get(1, TimeUnit.SECONDS);

    assertEquals(true, completed.getValue());;
  }

  @Test
  public void testGetAllOrWaitInitial() throws ExecutionException, InterruptedException, TimeoutException {
    BooleanHolder completed = new BooleanHolder();
    CompletableFuture<List<UiNotificationDo>> future = m_registry.getOrWait(Arrays.asList(createTopic("topic", null)), null, WAIT_TIMEOUT);
    future.thenApply(notifications -> {
      // Returns only subscription start notification if lastNotificationId is null
      assertEquals(Arrays.asList(createSubscriptionStartNotification("topic", -1)), notifications);
      completed.setValue(true);
      return notifications;
    });
    // Not waiting for notifications
    assertNull(m_registry.getListeners("topic"));

    // Wait for future to complete
    future.get(1, TimeUnit.SECONDS);

    assertEquals(true, completed.getValue());;
  }

  @Test
  public void testGetAllOrWaitMultiple() {
    IDoEntity message = createMessage();

    // A listener for topic
    BooleanHolder completed = new BooleanHolder(false);
    CompletableFuture<List<UiNotificationDo>> future = m_registry.getOrWait(Arrays.asList(createTopic("topic", -1)), null, WAIT_TIMEOUT);
    future.thenApply(notifications -> {
      assertEquals(Arrays.asList(createNotification(message, "topic", 1)), notifications);
      completed.setValue(true);
      return notifications;
    });
    assertEquals(1, m_registry.getListeners("topic").list().size());

    // Another listener for topic
    BooleanHolder completed2 = new BooleanHolder(false);
    CompletableFuture<List<UiNotificationDo>> future2 = m_registry.getOrWait(Arrays.asList(createTopic("topic", -1)), null, WAIT_TIMEOUT);
    future2.thenApply(notifications -> {
      assertEquals(Arrays.asList(createNotification(message, "topic", 1)), notifications);
      completed2.setValue(true);
      return notifications;
    });
    assertEquals(2, m_registry.getListeners("topic").list().size());

    // Listener for topic b
    BooleanHolder completed3 = new BooleanHolder(false);
    CompletableFuture<List<UiNotificationDo>> future3 = m_registry.getOrWait(Arrays.asList(createTopic("topic b", -1)), null, WAIT_TIMEOUT);
    future3.thenApply(notifications -> {
      assertEquals(Arrays.asList(createNotification(message, "topic b", 3)), notifications);
      completed3.setValue(true);
      return notifications;
    });
    assertEquals(2, m_registry.getListeners("topic").list().size());
    assertEquals(1, m_registry.getListeners("topic b").list().size());

    // Another listener for topic b
    BooleanHolder completed4 = new BooleanHolder(false);
    CompletableFuture<List<UiNotificationDo>> future4 = m_registry.getOrWait(Arrays.asList(createTopic("topic b", -1)), "otto", WAIT_TIMEOUT);
    future4.thenApply(notifications -> {
      assertEquals(Arrays.asList(createNotification(message, "topic b", 2)), notifications);
      completed4.setValue(true);
      return notifications;
    });
    assertEquals(2, m_registry.getListeners("topic").list().size());
    assertEquals(2, m_registry.getListeners("topic b").list().size());

    // Triggers both listeners for topic
    m_registry.put(message, "topic");
    assertNull(m_registry.getListeners("topic"));
    assertEquals(2, m_registry.getListeners("topic b").list().size());
    assertEquals(true, completed.getValue());;
    assertEquals(true, completed2.getValue());;

    // Triggers only Otto's listener for topic b
    m_registry.put(message, "topic b", "otto");
    assertEquals(1, m_registry.getListeners("topic b").list().size());
    assertEquals(false, completed3.getValue());;
    assertEquals(true, completed4.getValue());;

    // Triggers other listener for topic b
    m_registry.put(message, "topic b");
    assertNull(m_registry.getListeners("topic b"));
    assertEquals(true, completed3.getValue());;
  }


  @Test
  public void testGetAllOrWaitCancel() {
    BooleanHolder completed = new BooleanHolder();
    CompletableFuture<List<UiNotificationDo>> future = m_registry.getOrWait(Arrays.asList(createTopic("topic", -1)), null, 500);
    future.thenApply(notifications -> {
      assertTrue(notifications.isEmpty());
      completed.setValue(true);
      return notifications;
    });

    // Waiting for notifications
    assertEquals(1, m_registry.getListeners("topic").list().size());

    try {
      // Wait until timeout is reached
      future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      // nop
    }

    assertNull(m_registry.getListeners("topic"));
    assertEquals(true, completed.getValue());;
  }

  @Test
  public void testCleanup() throws InterruptedException {
    assertTrue(m_registry.getNotifications().isEmpty());

    m_registry.put(createMessage(), "topic", null, TimeUnit.MILLISECONDS.toMillis(500));
    m_registry.put(createMessage(), "topic", null, TimeUnit.MILLISECONDS.toMillis(50));
    assertEquals(2, m_registry.getNotifications().get("topic").size());

    Thread.sleep(51);
    m_registry.cleanup();
    assertEquals(1, m_registry.getNotifications().get("topic").size());

    Thread.sleep(450);
    m_registry.cleanup();
    assertTrue(m_registry.getNotifications().isEmpty());
  }

  protected IDoEntity createMessage(String value) {
    IDoEntity message = new DoEntity();
    message.put("dummy", value);
    return message;
  }

  protected IDoEntity createMessage() {
    return createMessage("value");
  }

  protected UiNotificationDo createNotification(IDoEntity message, String topic, Integer id) {
    UiNotificationDo notification = new UiNotificationDo()
        .withId(id)
        .withTopic(topic);
    if (message != null) {
      notification.withMessage(message);
    }
    return notification;
  }

  protected UiNotificationDo createSubscriptionStartNotification(String topic, Integer id) {
    return createNotification(null, topic, id).withSubscriptionStart(true);
  }

  protected TopicDo createTopic(String topic, Integer lastNotificationId) {
    return new TopicDo().withName(topic).withLastNotificationId(lastNotificationId);
  }
}
