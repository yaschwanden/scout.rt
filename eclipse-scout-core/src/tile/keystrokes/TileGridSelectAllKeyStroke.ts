/*
 * Copyright (c) 2014-2018 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
import {keys, ScoutKeyboardEvent, TileGrid, TileGridSelectKeyStroke} from '../../index';

export class TileGridSelectAllKeyStroke extends TileGridSelectKeyStroke {

  constructor(tileGrid: TileGrid) {
    super(tileGrid);
    this.ctrl = true;
    this.shift = false;
    this.which = [keys.A];
    this.renderingHints.$drawingArea = ($drawingArea, event) => {
      let tile = this.getSelectionHandler().getVisibleTiles()[0];
      if (tile) {
        // Draw in first tile so that other key stroke hints (e.g. left, right etc.) don't overlap this one
        return tile.$container;
      }
    };
  }

  protected override _accept(event: ScoutKeyboardEvent): boolean {
    let accepted = super._accept(event);
    if (!accepted) {
      return false;
    }
    if (!this.getSelectionHandler().isMultiSelect()) {
      return false;
    }
    return true;
  }

  override handle(event: ScoutKeyboardEvent) {
    this.getSelectionHandler().toggleSelection();
  }
}
