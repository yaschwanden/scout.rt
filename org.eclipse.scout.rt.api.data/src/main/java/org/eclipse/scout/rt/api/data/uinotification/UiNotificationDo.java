package org.eclipse.scout.rt.api.data.uinotification;

import javax.annotation.Generated;

import org.eclipse.scout.rt.dataobject.DoEntity;
import org.eclipse.scout.rt.dataobject.DoValue;
import org.eclipse.scout.rt.dataobject.IDoEntity;

public class UiNotificationDo extends DoEntity {
  public DoValue<Integer> id() {
    return doValue("id");
  }

  public DoValue<String> topic() {
    return doValue("topic");
  }

  public DoValue<IDoEntity> message() {
    return doValue("message");
  }

  /**
   * Marks the notification as initial subscription notification.
   * This notification will be returned, if the topic in the request does not contain a {@link TopicDo#lastNotificationId()}.
   * The message is empty. The client is only supposed to store the id as lastNotificationId and discard the notification.
   */
  public DoValue<Boolean> subscriptionStart() {
    return doValue("subscriptionStart");
  }

  /* **************************************************************************
   * GENERATED CONVENIENCE METHODS
   * *************************************************************************/

  @Generated("DoConvenienceMethodsGenerator")
  public UiNotificationDo withId(Integer id) {
    id().set(id);
    return this;
  }

  @Generated("DoConvenienceMethodsGenerator")
  public Integer getId() {
    return id().get();
  }

  @Generated("DoConvenienceMethodsGenerator")
  public UiNotificationDo withTopic(String topic) {
    topic().set(topic);
    return this;
  }

  @Generated("DoConvenienceMethodsGenerator")
  public String getTopic() {
    return topic().get();
  }

  @Generated("DoConvenienceMethodsGenerator")
  public UiNotificationDo withMessage(IDoEntity message) {
    message().set(message);
    return this;
  }

  @Generated("DoConvenienceMethodsGenerator")
  public IDoEntity getMessage() {
    return message().get();
  }

  /**
   * See {@link #subscriptionStart()}.
   */
  @Generated("DoConvenienceMethodsGenerator")
  public UiNotificationDo withSubscriptionStart(Boolean subscriptionStart) {
    subscriptionStart().set(subscriptionStart);
    return this;
  }

  /**
   * See {@link #subscriptionStart()}.
   */
  @Generated("DoConvenienceMethodsGenerator")
  public Boolean getSubscriptionStart() {
    return subscriptionStart().get();
  }

  /**
   * See {@link #subscriptionStart()}.
   */
  @Generated("DoConvenienceMethodsGenerator")
  public boolean isSubscriptionStart() {
    return nvl(getSubscriptionStart());
  }
}
