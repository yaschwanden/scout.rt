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
import {Lifecycle, ObjectModel, Widget} from '../index';

export interface LifecycleModel extends ObjectModel<Lifecycle<any>> {
  widget?: Widget;

  validationFailedTextKey?: string;
  validationFailedText?: string;

  emptyMandatoryElementsTextKey?: string;
  emptyMandatoryElementsText?: string;

  invalidElementsTextKey?: string;
  invalidElementsText?: string;

  askIfNeedSave?: boolean;
  saveChangesQuestionTextKey?: string;
  /** Java: cancelVerificationText */
  askIfNeedSaveText?: string;
}
