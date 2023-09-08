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

import org.eclipse.scout.rt.platform.util.ToStringBuilder;

public class UiNotificationRegistryElement {
  private UiNotificationMessageDo m_message;
  private Date m_validUntil;

  public UiNotificationMessageDo getMessage() {
    return m_message;
  }

  public void setMessage(UiNotificationMessageDo message) {
    m_message = message;
  }

  public Date getValidUntil() {
    return m_validUntil;
  }

  public void setValidUntil(Date validUntil) {
    m_validUntil = validUntil;
  }

  @Override
  public String toString() {
    ToStringBuilder builder = new ToStringBuilder(this);
    builder.attr("message", m_message);
    builder.attr("validUntil", m_validUntil);
    return builder.toString();
  }
}
