/*
 * Copyright (c) 2014-2017 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
package org.eclipse.scout.rt.ui.html.res.loader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.scout.rt.platform.util.IOUtility;
import org.eclipse.scout.rt.ui.html.IUiSession;
import org.eclipse.scout.rt.ui.html.UiSession;
import org.eclipse.scout.rt.ui.html.json.IJsonAdapter;

public class DynamicResourceInfo {

  public static final String PATH_PREFIX = "dynamic";
  /**
   * Pattern to determine if the provided url path is a dynamic resource path. Allow an additional / at the start.
   */
  public static final Pattern PATTERN_DYNAMIC_ADAPTER_RESOURCE_PATH = Pattern.compile("^/?" + PATH_PREFIX + "/([^/]*)/([^/]*)/(.*)$");

  private final String m_adapterId;
  private final String m_fileName;
  private final IUiSession m_uiSession;

  public DynamicResourceInfo(IJsonAdapter<?> jsonAdapter, String fileName) {
    this(jsonAdapter.getUiSession(), jsonAdapter.getId(), fileName);
  }

  public DynamicResourceInfo(IUiSession uiSession, String adapterId, String fileName) {
    m_adapterId = adapterId;
    m_fileName = fileName;
    m_uiSession = uiSession;
  }

  public String toPath() {
    String encodedFilename = IOUtility.urlEncode(getFileName());
    // / was encoded by %2F, revert this encoding otherwise filename doesn't look nice in browser download
    // Example for filesnames containing a /:
    // - relative reference from a unzipped zip file
    // - another path segment was explicitly added to distinguish between same filenames
    // Note that / is ignored while decoding, there is no need to revert this replacement before decoding
    encodedFilename = encodedFilename.replace("%2F", "/");
    return PATH_PREFIX + '/' + getUiSession().getUiSessionId() + '/' + getJsonAdapterId() + '/' + encodedFilename;
  }

  public IUiSession getUiSession() {
    return m_uiSession;
  }

  public String getJsonAdapterId() {
    return m_adapterId;
  }

  public String getFileName() {
    return m_fileName;
  }

  /**
   * Private, as one of the other methods that checks the session and adapter ids should be called instead
   *
   * @return array of the matched groups of PATTERN_DYNAMIC_ADAPTER_RESOURCE_PATH
   * @see #fromPath(HttpServletRequest, String)
   * @see #fromPath(IJsonAdapter, String)
   */
  private static String[] splitPath(String path) {
    if (path == null) {
      return null;
    }

    Matcher m = PATTERN_DYNAMIC_ADAPTER_RESOURCE_PATH.matcher(path);
    if (!m.matches()) {
      return null;
    }

    String uiSessionId = m.group(1);
    String adapterId = m.group(2);
    String filename = m.group(3);
    return new String[]{uiSessionId, adapterId, filename};
  }

  public static DynamicResourceInfo fromPath(IJsonAdapter<?> jsonAdapter, String path) {
    String[] parts = splitPath(path);
    if (parts == null) {
      return null;
    }
    // compare the session and adapter id with the one from the passed in adapter to ensure that
    // the resource path matches the passed in adapter and session
    if (!jsonAdapter.getUiSession().getUiSessionId().equals(parts[0])) {
      return null;
    }
    if (!jsonAdapter.getId().equals(parts[1])) {
      return null;
    }
    return new DynamicResourceInfo(jsonAdapter, IOUtility.urlDecode(parts[2]));
  }

  public static DynamicResourceInfo fromPath(HttpServletRequest req, String path) {
    String[] parts = splitPath(path);
    if (parts == null) {
      return null;
    }
    // lookup the UiSession on the current HttpSession to ensure the requested dynamic resource
    // is from one of the UiSessions of the currently authenticated user!
    IUiSession uiSession = UiSession.get(req, parts[0]);
    if (uiSession == null) {
      return null;
    }

    return new DynamicResourceInfo(uiSession, parts[1], IOUtility.urlDecode(parts[2]));
  }
}
