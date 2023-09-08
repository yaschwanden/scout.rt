/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.scout.rt.server.uinotification;

import org.eclipse.scout.rt.api.uinotification.UiNotificationMessageDo;
import org.eclipse.scout.rt.api.uinotification.UiNotificationRegistry;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.shared.notification.INotificationHandler;

public class UiNotificationClusterHandler implements INotificationHandler<UiNotificationMessageDo> {
  @Override
  public void handleNotification(UiNotificationMessageDo clusterNotification) {
    BEANS.get(UiNotificationRegistry.class).handleClusterNotification(clusterNotification);
  }
}
