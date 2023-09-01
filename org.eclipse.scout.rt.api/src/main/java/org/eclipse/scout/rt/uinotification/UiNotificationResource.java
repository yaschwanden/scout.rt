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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.security.auth.Subject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.container.TimeoutHandler;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

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
  private static final Logger LOG = LoggerFactory.getLogger(UiNotificationResource.class);

  private UiNotificationRegistry m_registry;

  public UiNotificationResource() {
    m_registry = BEANS.get(UiNotificationRegistry.class);
  }

  protected UiNotificationRegistry getRegistry() {
    return m_registry;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void get(UiNotificationRequest request, @Suspended AsyncResponse asyncResponse) {
    String userId = getUserId();
    List<String> topics = request.getTopics();
    LOG.info("Received request for topics {} and user {}", topics, userId);

    asyncResponse.setTimeout(30, TimeUnit.SECONDS);
    CompletableFuture<Boolean> future = getRegistry().getAllOrWait(topics, userId, request.getLastId(), request.getLongPolling())
        .thenApply((notifications) -> {
          LOG.info("Resuming async response with {} notifications for topics {} and user {}", notifications.size(), topics, userId);
          return asyncResponse.resume((new UiNotificationResponse().withNotifications(notifications)));
        })
        .exceptionally(e -> {
          LOG.error("Error completing future", e);
          return asyncResponse.resume(Response.status(Status.INTERNAL_SERVER_ERROR).entity(e).build());
        });

    asyncResponse.setTimeoutHandler(ar -> {
      LOG.info("Timeout reached.");
      future.cancel(false);
      if (!ar.isDone()) {
        ar.resume(new UiNotificationResponse());
      }
    });
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
    getRegistry().put(doEntity, "sample", null);
  }
}
