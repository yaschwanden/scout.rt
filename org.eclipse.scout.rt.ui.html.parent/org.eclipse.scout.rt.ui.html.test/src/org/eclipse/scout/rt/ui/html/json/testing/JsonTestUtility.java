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
package org.eclipse.scout.rt.ui.html.json.testing;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.scout.rt.ui.html.json.IJsonSession;
import org.eclipse.scout.rt.ui.html.json.JsonEvent;
import org.eclipse.scout.rt.ui.html.json.JsonException;
import org.eclipse.scout.rt.ui.html.json.JsonRequest;
import org.eclipse.scout.rt.ui.html.json.JsonResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.mockito.Mockito;

public class JsonTestUtility {

  public static IJsonSession createAndInitializeJsonSession() {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getLocale()).thenReturn(new Locale("de_CH"));
    Mockito.when(request.getHeader("User-Agent")).thenReturn("dummy");

    JSONObject jsonReqObj = new JSONObject();
    try {
      jsonReqObj.put(JsonRequest.PROP_SESSION_PART_ID, "1.1");
    }
    catch (JSONException e) {
      throw new JsonException(e);
    }
    JsonRequest jsonRequest = new JsonRequest(jsonReqObj);

    IJsonSession jsonSession = new TestEnvironmentJsonSession();
    jsonSession.init(request, jsonRequest);

    return jsonSession;
  }

  public static JsonEvent createJsonEvent(String type) throws JSONException {
    return createJsonEvent(type, null);
  }

  public static JsonEvent createJsonEvent(String type, String id) throws JSONException {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(JsonEvent.TYPE, type);
    jsonObject.put(JsonEvent.ID, id);
    return new JsonEvent(jsonObject);
  }

  public static List<JSONObject> extractEventsFromResponse(JsonResponse response, String eventType) throws JSONException {
    return extractEventsFromResponse(response, eventType, true);
  }

  /**
   * @return all events of the given type. Never null.
   */
  public static List<JSONObject> extractEventsFromResponse(JsonResponse response, String eventType, boolean flush) throws JSONException {
    List<JSONObject> list = new LinkedList<>();
    for (JSONObject responseEvent : response.getEventList()) {
      if (eventType.equals(responseEvent.getString(JsonEvent.TYPE))) {
        responseEvent = JsonTestUtility.resolveJsonObject(responseEvent);
        list.add(responseEvent);
      }
    }
    return list;
  }

  public static void assertGC(WeakReference<?> ref) {
    int maxRuns = 50;
    for (int i = 0; i < maxRuns; i++) {
      if (ref.get() == null) {
        return;
      }

      System.gc();
      try {
        Thread.sleep(50);
      }
      catch (InterruptedException e) {
      }
    }

    Assert.fail("Potential memory leak, object " + ref.get() + "still exists after gc");
  }

  public static JSONObject resolveJsonObject(JSONObject object) throws JSONException {
    return JsonResponse.resolveJsonAdapter(object);
  }

  public static List<JSONObject> resolveJsonAdapters(List<JSONObject> objects) throws JSONException {
    List<JSONObject> result = new ArrayList<>(objects.size());
    for (JSONObject obj : objects) {
      result.add(resolveJsonObject(obj));
    }
    return result;
  }

}
