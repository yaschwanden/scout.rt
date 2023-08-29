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

import java.security.AccessController;
import java.util.List;

import javax.security.auth.Subject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.scout.rt.api.data.uinotification.UiNotificationDo;
import org.eclipse.scout.rt.api.data.uinotification.UiNotificationRequest;
import org.eclipse.scout.rt.api.data.uinotification.UiNotificationResponse;
import org.eclipse.scout.rt.dataobject.DoEntity;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.util.CollectionUtility;
import org.eclipse.scout.rt.rest.IRestResource;
import org.eclipse.scout.rt.security.IAccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("ui-notifications")
public class UiNotificationResource implements IRestResource {
  private UiNotificationRegistry m_registry;

  public UiNotificationResource() {
    m_registry = BEANS.get(UiNotificationRegistry.class);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public UiNotificationResponse get(UiNotificationRequest request) {
    List<UiNotificationDo> notifications;
    if (request.isLongPolling()) {
      notifications = m_registry.poll(request.getTopics(), getUserId(), request.getLastId());
    }
    else {
      notifications = m_registry.getAll(request.getTopics(), getUserId(), request.getLastId());
    }
    return new UiNotificationResponse().withNotifications(notifications);
  }

  protected String getUserId() {
    return BEANS.get(IAccessControlService.class).getUserIdOfCurrentSubject();
  }

  @Path("sample")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void putSample() {
    DoEntity doEntity = new DoEntity();
    doEntity.put("sample", "data " + System.currentTimeMillis());
    m_registry.put(doEntity, "sample", null);
  }
}
