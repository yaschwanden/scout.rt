/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.scout.rt.rest.uinotification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.scout.rt.dataobject.DoEntity;
import org.eclipse.scout.rt.rest.IRestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("notifications")
public class UiNotificationResource implements IRestResource {
  private static final Logger LOG = LoggerFactory.getLogger(UiNotificationResource.class);
  private static BlockingQueue<DoEntity> s_notifications = new LinkedBlockingQueue<>();
  private Integer m_timeout = 60000; // TODO CGU CN Add new config property / move existing one BackgroundPollingIntervalProperty


  @POST
  @Path("poll")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public UiNotificationResponse poll() { // TODO CGU CN @Suspended AsyncResponse response geht nicht mit jersey 2.x, 3.x is jakarta...
    LOG.info("Waiting for new ui notifications...");
    DoEntity element = null;
    try {
      element = s_notifications.poll(m_timeout, TimeUnit.MILLISECONDS);
      // TODO CGU CN consider session timeout, add/move MaxUserIdleTimeProperty? Should we add this to PollProxyRequestHandler as well / only?
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    List<DoEntity> notifications = new ArrayList<>();
    notifications.add(element);
    s_notifications.drainTo(notifications);
//    response.resume(new ClientNotificationResponse().withNotifications(notifications));
    s_notifications.clear();
    LOG.info("Returning new ui notification: {}.", notifications);
    return new UiNotificationResponse().withNotifications(notifications);
  }

  @Path("sample")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void putSample() {
    // TODO CGU CN how to put notifications? Add service that can be used from UiSession, crm.server and studio.core / api
    // TODO CGU CN message needs to be published over cluster, crm and studio use different service -> use studio service only? make abstraction?
    DoEntity doEntity = new DoEntity();
    doEntity.put("sample", "data");
    try {
      s_notifications.put(doEntity);
      LOG.info("Added new ui notification: {}. New size: {}", doEntity, s_notifications.size());
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
