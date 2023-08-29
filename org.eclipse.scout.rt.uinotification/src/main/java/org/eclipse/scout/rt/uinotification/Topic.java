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

import java.util.Objects;

public class Topic {
  private String m_topic;
  private String m_user;

  public String getTopic() {
    return m_topic;
  }

  public void setTopic(String topic) {
    m_topic = topic;
  }

  public String getUser() {
    return m_user;
  }

  public void setUser(String user) {
    m_user = user;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Topic topic = (Topic) o;
    return Objects.equals(m_topic, topic.m_topic) && Objects.equals(m_user, topic.m_user);
  }

  @Override
  public int hashCode() {
    return Objects.hash(m_topic, m_user);
  }

  @Override
  public String toString() {
    return "Topic { topic: " + m_topic + ", user: " + m_user + " }";
  }
}
