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
import {AbstractLayout, DialogLayout, graphics, HtmlComponent, MessageBox, scrollbars} from '../index';

export class MessageBoxLayout extends AbstractLayout {
  messageBox: MessageBox;

  constructor(messageBox: MessageBox) {
    super();
    this.messageBox = messageBox;
  }

  override layout($container: JQuery) {
    let htmlComp = HtmlComponent.get($container),
      windowSize = $container.windowSize(),
      currentBounds = htmlComp.offsetBounds(true),
      messageBoxSize = htmlComp.size(),
      messageBoxMargins = htmlComp.margins();

    messageBoxSize = DialogLayout.fitContainerInWindow(windowSize, currentBounds.point(), messageBoxSize, messageBoxMargins);

    // Add markers to be able to style the msg box in a different way when it uses the full width or height
    $container
      .toggleClass('full-width', (currentBounds.x === 0 && messageBoxMargins.horizontal() === 0 && windowSize.width === messageBoxSize.width))
      .toggleClass('full-height', (currentBounds.y === 0 && messageBoxMargins.vertical() === 0 && windowSize.height === messageBoxSize.height));

    graphics.setSize($container, messageBoxSize);

    let buttonsSize = graphics.size(this.messageBox.$buttons, {
      exact: true
    });
    this.messageBox.$content.css('height', 'calc(100% - ' + buttonsSize.height + 'px)');
    scrollbars.update(this.messageBox.$content);

    $container.cssPosition(DialogLayout.positionContainerInWindow($container));
  }
}
