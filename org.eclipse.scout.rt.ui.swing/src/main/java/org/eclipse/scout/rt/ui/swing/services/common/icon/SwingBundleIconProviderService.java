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
package org.eclipse.scout.rt.ui.swing.services.common.icon;

import org.eclipse.scout.rt.client.services.common.icon.AbstractIconProviderService;

public class SwingBundleIconProviderService extends AbstractIconProviderService {

  public static final String FOLDER_NAME = "org.eclipse.scout.rt.ui.swing.icons";

  public SwingBundleIconProviderService() {
    setFolderName(FOLDER_NAME);
  }
}
