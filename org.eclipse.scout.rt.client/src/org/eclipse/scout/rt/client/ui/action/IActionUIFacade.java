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
package org.eclipse.scout.rt.client.ui.action;

public interface IActionUIFacade {

  /**
   * toggable actions must call this method in addition to the {@link #fireActionFromUI()} method on selection change
   * event.
   * 
   * @param b
   */
  void setSelectedFromUI(boolean b);

  /**
   * all actions must call this method when the UI action is invoked. In case of toggable action
   * {@link #fireActionFromUI()} is also called if the selection state does not change.
   */
  void fireActionFromUI();

}
