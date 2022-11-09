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
import {FormFieldLayout, graphics, Rectangle, StringField} from '../../../index';

export class StringFieldLayout extends FormFieldLayout {

  constructor(stringField: StringField) {
    super(stringField);
  }

  protected override _layoutClearIcon(formField: StringField, fieldBounds: Rectangle, right: number, top: number) {
    if (formField.$icon && formField.$icon.isVisible()) {
      right += graphics.prefSize(formField.$icon, true).width;
    }
    super._layoutClearIcon(formField, fieldBounds, right, top);
  }
}
