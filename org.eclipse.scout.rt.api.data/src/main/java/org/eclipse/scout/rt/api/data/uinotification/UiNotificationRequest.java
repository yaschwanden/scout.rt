/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.scout.rt.api.data.uinotification;

import java.util.List;

import javax.annotation.Generated;

import org.eclipse.scout.rt.dataobject.DoEntity;
import org.eclipse.scout.rt.dataobject.DoValue;

public class UiNotificationRequest extends DoEntity {

  public DoValue<List<String>> topics() {
    return doValue("topics");
  }

  public DoValue<Integer> lastId() {
    return doValue("lastId");
  }

  public DoValue<Boolean> longPolling() {
    return doValue("longPolling");
  }

  /* **************************************************************************
   * GENERATED CONVENIENCE METHODS
   * *************************************************************************/

  @Generated("DoConvenienceMethodsGenerator")
  public UiNotificationRequest withTopics(List<String> topics) {
    topics().set(topics);
    return this;
  }

  @Generated("DoConvenienceMethodsGenerator")
  public List<String> getTopics() {
    return topics().get();
  }

  @Generated("DoConvenienceMethodsGenerator")
  public UiNotificationRequest withLastId(Integer lastId) {
    lastId().set(lastId);
    return this;
  }

  @Generated("DoConvenienceMethodsGenerator")
  public Integer getLastId() {
    return lastId().get();
  }

  @Generated("DoConvenienceMethodsGenerator")
  public UiNotificationRequest withLongPolling(Boolean longPolling) {
    longPolling().set(longPolling);
    return this;
  }

  @Generated("DoConvenienceMethodsGenerator")
  public Boolean getLongPolling() {
    return longPolling().get();
  }

  @Generated("DoConvenienceMethodsGenerator")
  public boolean isLongPolling() {
    return nvl(getLongPolling());
  }
}
