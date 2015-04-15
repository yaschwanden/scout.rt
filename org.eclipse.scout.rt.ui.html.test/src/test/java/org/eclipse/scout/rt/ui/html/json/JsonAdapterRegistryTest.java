/*******************************************************************************
 * Copyright (c) 2010 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 ******************************************************************************/
package org.eclipse.scout.rt.ui.html.json;

import static org.junit.Assert.assertEquals;

import org.eclipse.scout.rt.client.testenvironment.TestEnvironmentClientSession;
import org.eclipse.scout.rt.client.ui.form.fields.stringfield.AbstractStringField;
import org.eclipse.scout.rt.client.ui.form.fields.stringfield.IStringField;
import org.eclipse.scout.rt.testing.client.runner.ClientTestRunner;
import org.eclipse.scout.rt.testing.client.runner.RunWithClientSession;
import org.eclipse.scout.rt.testing.platform.runner.RunWithSubject;
import org.eclipse.scout.rt.ui.html.UiSession;
import org.eclipse.scout.rt.ui.html.UiSessionTest;
import org.eclipse.scout.rt.ui.html.json.fixtures.UiSessionMock;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ClientTestRunner.class)
@RunWithSubject("default")
@RunWithClientSession(TestEnvironmentClientSession.class)
public class JsonAdapterRegistryTest {

  @Test
  public void testCreateDisposeInSameRequest() throws Exception {
    UiSession session = new UiSessionMock();
    IStringField model = new AbstractStringField() {
    };
    IJsonAdapter<?> adapter = session.getOrCreateJsonAdapter(model, null);

    // Note: Additionally, registry contains the "root adapter" and a context-menu
    assertEquals(3, UiSessionTest.getJsonAdapterRegistry(session).getJsonAdapterCount());
    assertEquals(2, session.currentJsonResponse().adapterMap().size());
    assertEquals(0, session.currentJsonResponse().eventList().size());

    model.setDisplayText("Test");
    assertEquals(3, UiSessionTest.getJsonAdapterRegistry(session).getJsonAdapterCount());
    assertEquals(2, session.currentJsonResponse().adapterMap().size());
    assertEquals(1, session.currentJsonResponse().eventList().size());

    adapter.dispose();
    assertEquals(1, UiSessionTest.getJsonAdapterRegistry(session).getJsonAdapterCount());
    assertEquals(0, session.currentJsonResponse().adapterMap().size());
    assertEquals(0, session.currentJsonResponse().eventList().size());
  }
}
