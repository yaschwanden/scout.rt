/*
 * Copyright (c) 2010-2022 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
import {FileChooserField, HAlign, keys, KeyStroke} from '../../../index';

export class FileChooserFieldDeleteKeyStroke extends KeyStroke {
  declare field: FileChooserField;

  constructor(field: FileChooserField) {
    super();
    this.field = field;
    this.which = [keys.DELETE];
    this.stopPropagation = true;
    this.renderingHints.offset = 25;
    this.renderingHints.hAlign = HAlign.RIGHT;
    this.renderingHints.$drawingArea = ($drawingArea, event) => this.field.$fieldContainer;
  }

  override handle(event: JQuery.KeyboardEventBase) {
    this.field.clear();
  }
}
