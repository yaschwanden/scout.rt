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

import java.util.Date;
import java.util.Objects;

import org.eclipse.scout.rt.api.data.uinotification.UiNotificationDo;

public class UiNotificationElement {
  private String m_user;
  private UiNotificationDo m_notification;
  private Date m_validUntil;

  public String getUser() {
    return m_user;
  }

  public void setUser(String user) {
    m_user = user;
  }

  public UiNotificationDo getNotification() {
    return m_notification;
  }

  public void setNotification(UiNotificationDo notification) {
    m_notification = notification;
  }

  public Date getValidUntil() {
    return m_validUntil;
  }

  public void setValidUntil(Date validUntil) {
    m_validUntil = validUntil;
  }

  @Override
  public String toString() {
    return "UiNotificationElement { notification: " + m_notification + ", user: " + m_user + " }";
  }
}
