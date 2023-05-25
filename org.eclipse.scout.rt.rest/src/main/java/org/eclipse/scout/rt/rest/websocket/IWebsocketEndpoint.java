/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.scout.rt.rest.websocket;

import javax.websocket.Session;

import org.eclipse.scout.rt.platform.Bean;

@Bean
public interface IWebsocketEndpoint {

  static String sessionForLog(Session session) {
    String str = session.getRequestURI().toString();
    if (session.getQueryString() != null) {
      str += "?" + session.getQueryString();
    }
    return str;
  }
}
