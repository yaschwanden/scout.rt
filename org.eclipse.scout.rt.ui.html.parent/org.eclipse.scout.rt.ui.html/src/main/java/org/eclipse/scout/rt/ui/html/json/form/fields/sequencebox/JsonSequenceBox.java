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
package org.eclipse.scout.rt.ui.html.json.form.fields.sequencebox;

import org.eclipse.scout.rt.client.ui.form.fields.sequencebox.ISequenceBox;
import org.eclipse.scout.rt.ui.html.json.IJsonSession;
import org.eclipse.scout.rt.ui.html.json.form.fields.JsonFormField;
import org.json.JSONObject;

/**
 * This class creates JSON output for an <code>ISequenceBox</code>.
 */
public class JsonSequenceBox extends JsonFormField<ISequenceBox> {

  public static final String PROP_FIELDS = "fields";

  public JsonSequenceBox(ISequenceBox model, IJsonSession session, String id) {
    super(model, session, id);
  }

  @Override
  public String getObjectType() {
    return "SequenceBox";
  }

  @Override
  public JSONObject toJson() {
    return putProperty(super.toJson(), PROP_FIELDS, getOrCreateJsonAdapters(getModel().getFields()));
  }

  @Override
  public void dispose() {
    disposeJsonAdapters(getModel().getFields());
    super.dispose();
  }

}
