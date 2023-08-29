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
}
