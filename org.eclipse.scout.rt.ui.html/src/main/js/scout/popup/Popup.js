/*******************************************************************************
 * Copyright (c) 2014-2018 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 ******************************************************************************/
scout.Popup = function() {
  scout.Popup.parent.call(this);

  this._mouseDownHandler = null;
  this._anchorScrollHandler = null;
  this._popupOpenHandler = null;
  this._glassPaneRenderer = null;
  this.anchorBounds = null;
  this.animateOpening = false;
  this.$anchor = null;
  this.windowPaddingX = 10;
  this.windowPaddingY = 5;
  this.withGlassPane = false;
  this.withFocusContext = true;
  this.initialFocus = function() {
    return scout.focusRule.AUTO;
  };
  this.focusableContainer = false;

  // The alignment defines how the popup is positioned around the anchor.
  // If there is no anchor or anchor bounds the alignment has no effect.
  this.horizontalAlignment = scout.Popup.Alignment.LEFTEDGE;
  this.verticalAlignment = scout.Popup.Alignment.BOTTOM;

  // If switch is enabled, the alignment will be changed if the popup overlaps a window border.
  // Currently only switching form right to left and bottom to top is supported.
  // Switching from leftedge to rightedge and topedge to bottomedge does not work if withArrow is true
  this.horizontalSwitch = false;
  this.verticalSwitch = true;

  // Hints for the layout to control whether the size should be adjusted if the popup does not fit into the window.
  // Before trimming is applied the popup will be switched, if the switch option is enabled.
  // If neither switch nor trim is enabled, the popup will be moved from right to left until its right border is visible.
  // This moving is currently only supported for the right window border. If it overlaps any other border it won't be moved.
  this.trimWidth = false;
  this.trimHeight = true;

  // Defines what should happen when the scroll parent is scrolled
  this.scrollType = 'remove';

  // If true, the anchor is considered when computing the position and size of the popup
  this.boundToAnchor = true;

  // If true, an arrow is shown pointing to the anchor. If there is no anchor, no arrow will be visible.
  // Please note: some alignment combinations are not supported, which are: LEFT or RIGHT + BOTTOM or TOP
  this.withArrow = false;

  // If false, the attached mouse down handler will NOT close the popup if the anchor was clicked, the anchor is responsible to close it.
  // This is necessary because the mousedown listener is attached to the capture phase and therefore executed before any other.
  // If anchor was clicked, popup would already be closed and then opened again -> popup could never be closed by clicking the anchor
  this.closeOnAnchorMouseDown = true;

  // Defines whether the popup should be closed on a mouse click outside of the popup
  this.closeOnMouseDownOutside = true;

  // Defines whether the popup should be closed whenever another popup opens.
  this.closeOnOtherPopupOpen = true;

  this.$arrow = null;
};
scout.inherits(scout.Popup, scout.Widget);

// Note that these strings are also used as CSS classes
scout.Popup.Alignment = {
  LEFT: 'left',
  LEFTEDGE: 'leftedge',
  TOP: 'top',
  TOPEDGE: 'topedge',
  CENTER: 'center',
  RIGHT: 'right',
  RIGHTEDGE: 'rightedge',
  BOTTOM: 'bottom',
  BOTTOMEDGE: 'bottomedge'
};

scout.Popup.SwitchRule = {};
(function() {
  // Initialize switch rules (wrapped in IIFE to have local function scope for the variables)
  var SwitchRule = scout.Popup.SwitchRule;
  var Alignment = scout.Popup.Alignment;
  SwitchRule[Alignment.LEFT] = Alignment.RIGHT;
  SwitchRule[Alignment.LEFTEDGE] = Alignment.RIGHTEDGE;
  SwitchRule[Alignment.TOP] = Alignment.BOTTOM;
  SwitchRule[Alignment.TOPEDGE] = Alignment.BOTTOMEDGE;
  SwitchRule[Alignment.CENTER] = Alignment.CENTER;
  SwitchRule[Alignment.RIGHT] = Alignment.LEFT;
  SwitchRule[Alignment.RIGHTEDGE] = Alignment.LEFTEDGE;
  SwitchRule[Alignment.BOTTOM] = Alignment.TOP;
  SwitchRule[Alignment.BOTTOMEDGE] = Alignment.TOPEDGE;
}());

/**
 * @param options:
 *          initialFocus: a function that returns the element to be focused or a <code>scout.focusRule</code>. Default returns <code>scout.focusRule.AUTO</code>
 *          focusableContainer: a boolean whether or not the container of the Popup is focusable
 */
scout.Popup.prototype._init = function(options) {
  scout.Popup.parent.prototype._init.call(this, options);

  if (options.location) {
    this.anchorBounds = new scout.Rectangle(options.location.x, options.location.y, 0, 0);
  }
  if (this.withGlassPane) {
    this._glassPaneRenderer = new scout.GlassPaneRenderer(this);
  }
};

/**
 * @override
 */
scout.Popup.prototype._createKeyStrokeContext = function() {
  return new scout.KeyStrokeContext();
};

/**
 * @override
 */
scout.Popup.prototype._initKeyStrokeContext = function() {
  scout.Popup.parent.prototype._initKeyStrokeContext.call(this);

  this.keyStrokeContext.registerKeyStroke(this._createCloseKeyStroke());
};

/**
 * Override this method to provide a key stroke which closes the popup.
 * The default impl. returns a CloseKeyStroke which handles the ESC key.
 */
scout.Popup.prototype._createCloseKeyStroke = function() {
  return new scout.CloseKeyStroke(this);
};

scout.Popup.prototype._createLayout = function() {
  return new scout.PopupLayout(this);
};

scout.Popup.prototype.open = function($parent) {
  this._triggerPopupOpenEvent();

  this._open($parent);

  // Focus the popup
  // It is important that this happens after layouting and positioning, otherwise we'd focus an element
  // that is currently not on the screen. Which would cause the whole desktop to
  // be shifted for a few pixels.
  if (this.withFocusContext) {
    this.session.focusManager.installFocusContext(this.$container, this.initialFocus());
  }
  if (this.animateOpening) {
    this.$container.addClassForAnimation('animate-open');
  }
};

scout.Popup.prototype._open = function($parent) {
  this.render($parent);
  this.revalidateLayout();
  this.position();
};

scout.Popup.prototype.render = function($parent) {
  var $popupParent = $parent || this.entryPoint();
  scout.Popup.parent.prototype.render.call(this, $popupParent);
};

scout.Popup.prototype._render = function() {
  this.$container = this.$parent.appendDiv('popup');
  this.htmlComp = scout.HtmlComponent.install(this.$container, this.session);
  this.htmlComp.validateRoot = true;
  this.htmlComp.setLayout(this._createLayout());
};

scout.Popup.prototype._renderProperties = function() {
  scout.Popup.parent.prototype._renderProperties.call(this);
  this._renderWithArrow();
  this._renderWithFocusContext();
  this._renderWithGlassPane();
};

scout.Popup.prototype._postRender = function() {
  scout.Popup.parent.prototype._postRender.call(this);

  this.size();
  this._attachCloseHandler();
};

scout.Popup.prototype._remove = function() {
  if (this._glassPaneRenderer) {
    this._glassPaneRenderer.removeGlassPanes();
  }
  if (this.withFocusContext) {
    this.session.focusManager.uninstallFocusContext(this.$container);
  }
  if (this.$arrow) {
    this.$arrow.remove();
    this.$arrow = null;
  }
  // remove all clean-up handlers
  this._detachCloseHandler();
  scout.Popup.parent.prototype._remove.call(this);
};

scout.Popup.prototype._renderWithFocusContext = function() {
  // Add programmatic 'tabindex' if the $container itself should be focusable (used by context menu popups with no focusable elements)
  if (this.withFocusContext && this.focusableContainer) {
    this.$container.attr('tabindex', -1);
  }
};

scout.Popup.prototype._renderWithGlassPane = function() {
  if (this._glassPaneRenderer) {
    this._glassPaneRenderer.renderGlassPanes();
  }
};

scout.Popup.prototype.setWithArrow = function(withArrow) {
  this.setProperty('withArrow', withArrow);
};

scout.Popup.prototype._renderWithArrow = function() {
  if (this.$arrow) {
    this.$arrow.remove();
    this.$arrow = null;
  }
  if (this.withArrow) {
    this.$arrow = this.$container.prependDiv('popup-arrow');
    this._updateArrowClass();
  }
  this.invalidateLayoutTree();
};

scout.Popup.prototype._updateArrowClass = function(verticalAlignment, horizontalAlignment) {
  if (this.$arrow) {
    this.$arrow.removeClass(this._alignClasses());
    this.$arrow.addClass(this._computeArrowPositionClass(verticalAlignment, horizontalAlignment));
  }
};

scout.Popup.prototype._computeArrowPositionClass = function(verticalAlignment, horizontalAlignment) {
  var Alignment = scout.Popup.Alignment;
  var cssClass = '';
  horizontalAlignment = horizontalAlignment || this.horizontalAlignment;
  verticalAlignment = verticalAlignment || this.verticalAlignment;
  switch (horizontalAlignment) {
    case Alignment.LEFT:
      cssClass = Alignment.RIGHT;
      break;
    case Alignment.RIGHT:
      cssClass = Alignment.LEFT;
      break;
    default:
      cssClass = horizontalAlignment;
      break;
  }

  switch (verticalAlignment) {
    case Alignment.BOTTOM:
      cssClass += ' ' + Alignment.TOP;
      break;
    case Alignment.TOP:
      cssClass += ' ' + Alignment.BOTTOM;
      break;
    default:
      cssClass += ' ' + verticalAlignment;
      break;
  }
  return cssClass;
};

scout.Popup.prototype._isRemovalPrevented = function() {
  // Never prevent. Default returns true if removal is pending by an animation, but popups should be closed before the animation starts
  return false;
};

scout.Popup.prototype.close = function() {
  this.trigger('close');
  this.destroy();
};

/**
 * Install listeners to close the popup once clicking outside the popup,
 * or changing the anchor's scroll position, or another popup is opened.
 */
scout.Popup.prototype._attachCloseHandler = function() {
  // Install mouse close handler
  // The listener needs to be executed in the capturing phase -> prevents that _onMouseDown will be executed right after the popup gets opened using mouse down, otherwise the popup would be closed immediately
  if (this.closeOnMouseDownOutside) {
    this._mouseDownHandler = this._onMouseDown.bind(this);
    this.$container.document(true).addEventListener('mousedown', this._mouseDownHandler, true); // true=the event handler is executed in the capturing phase
  }

  // Install popup open close handler
  if (this.closeOnOtherPopupOpen) {
    this._popupOpenHandler = this._onPopupOpen.bind(this);
    this.session.desktop.on('popupOpen', this._popupOpenHandler);
  }

  // Install scroll close handler
  this._attachAnchorScrollHandler();
};

scout.Popup.prototype._attachAnchorScrollHandler = function() {
  if (this.$anchor && this.boundToAnchor && this.scrollType) {
    this._anchorScrollHandler = this._onAnchorScroll.bind(this);
    scout.scrollbars.onScroll(this.$anchor, this._anchorScrollHandler);
  }
};

scout.Popup.prototype._detachAnchorScrollHandler = function() {
  if (this._anchorScrollHandler) {
    scout.scrollbars.offScroll(this._anchorScrollHandler);
    this._anchorScrollHandler = null;
  }
};

scout.Popup.prototype._detachCloseHandler = function() {
  // Uninstall scroll close handler
  this._detachAnchorScrollHandler();

  // Uninstall popup open close handler
  if (this._popupOpenHandler) {
    this.session.desktop.off('popupOpen', this._popupOpenHandler);
    this._popupOpenHandler = null;
  }

  // Uninstall mouse close handler
  if (this._mouseDownHandler) {
    this.$container.document(true).removeEventListener('mousedown', this._mouseDownHandler, true);
    this._mouseDownHandler = null;
  }
};

scout.Popup.prototype._onMouseDown = function(event) {
  // in some cases the mousedown handler is executed although it has been already
  // detached on the _remove() method. However, since we're in the middle of
  // processing the mousedown event, it's too late to detach the event and we must
  // deal with that situation by checking the rendered flag. Otherwise we would
  // run into an error later, since the $container is not available anymore.
  if (!this.rendered) {
    return;
  }
  if (this._isMouseDownOutside(event)) {
    this._onMouseDownOutside(event);
  }
};

scout.Popup.prototype._isMouseDownOutside = function(event) {
  var $target = $(event.target),
    targetWidget;

  if (!this.closeOnAnchorMouseDown && this._isMouseDownOnAnchor(event)) {
    // 1. Often times, click on the anchor opens and 2. click closes the popup
    // If we were closing the popup here, it would not be possible to achieve the described behavior anymore -> let anchor handle open and close.
    return false;
  }

  targetWidget = scout.widget($target);

  // close the popup only if the click happened outside of the popup and its children
  // It is not sufficient to check the dom hierarchy using $container.has($target)
  // because the popup may open other popups which probably is not a dom child but a sibling
  // Also ignore clicks if the popup is covert by a glasspane
  return !this.isOrHas(targetWidget) && !this.session.focusManager.isElementCovertByGlassPane(this.$container[0]);
};

scout.Popup.prototype._isMouseDownOnAnchor = function(event) {
  return !!this.$anchor && this.$anchor.isOrHas(event.target);
};

/**
 * Method invoked once a mouse down event occurs outside the popup.
 */
scout.Popup.prototype._onMouseDownOutside = function(event) {
  this.close();
};

/**
 * Method invoked once the 'options.$anchor' is scrolled.
 */
scout.Popup.prototype._onAnchorScroll = function(event) {
  if (!this.rendered) {
    // Scroll events may be fired delayed, even if scroll listener are already removed.
    return;
  }
  if (this.scrollType === 'position') {
    this.position();
  } else if (this.scrollType === 'layoutAndPosition') {
    this.revalidateLayout();
    this.position();
  } else if (this.scrollType === 'remove') {
    this.close();
  }
};

/**
 * Method invoked once a popup is opened.
 */
scout.Popup.prototype._onPopupOpen = function(event) {
  // Make sure child popups don't close the parent popup, we must check parent hierarchy in both directions
  // Use case: Opening of a context menu or cell editor in a form popup
  // Also, popups covered by a glass pane (a modal dialog is open) must never be closed
  // Use case: popup opens a modal dialog. User clicks on a smartfield on this dialog -> underlying popup must not get closed
  var closable = !this.isOrHas(event.popup) &&
    !event.popup.isOrHas(this);
  if (this.rendered) {
    closable = closable && !this.session.focusManager.isElementCovertByGlassPane(this.$container[0]);
  }
  if (closable) {
    this.close();
  }
};

scout.Popup.prototype.setHorizontalAlignment = function(horizontalAlignment) {
  this.setProperty('horizontalAlignment', horizontalAlignment);
};

scout.Popup.prototype._renderHorizontalAlignment = function() {
  this._updateArrowClass();
  this.invalidateLayoutTree();
};

scout.Popup.prototype.setVerticalAlignment = function(verticalAlignment) {
  this.setProperty('verticalAlignment', verticalAlignment);
};

scout.Popup.prototype._renderVerticalAlignment = function() {
  this._updateArrowClass();
  this.invalidateLayoutTree();
};

scout.Popup.prototype.setHorizontalSwitch = function(horizontalSwitch) {
  this.setProperty('horizontalSwitch', horizontalSwitch);
};

scout.Popup.prototype._renderHorizontalSwitch = function() {
  this.invalidateLayoutTree();
};

scout.Popup.prototype.setVerticalSwitch = function(verticalSwitch) {
  this.setProperty('verticalSwitch', verticalSwitch);
};

scout.Popup.prototype._renderVerticalSwitch = function() {
  this.invalidateLayoutTree();
};

scout.Popup.prototype.setTrimWidth = function(trimWidth) {
  this.setProperty('trimWidth', trimWidth);
};

scout.Popup.prototype._renderTrimWidth = function() {
  this.invalidateLayoutTree();
};

scout.Popup.prototype.setTrimHeight = function(trimHeight) {
  this.setProperty('trimHeight', trimHeight);
};

scout.Popup.prototype._renderTrimHeight = function() {
  this.invalidateLayoutTree();
};

scout.Popup.prototype.prefLocation = function(verticalAlignment, horizontalAlignment) {
  if (!this.boundToAnchor || (!this.anchorBounds && !this.$anchor)) {
    return this._prefLocationWithoutAnchor();
  }
  return this._prefLocationWithAnchor(verticalAlignment, horizontalAlignment);
};

scout.Popup.prototype._prefLocationWithoutAnchor = function() {
  return scout.DialogLayout.positionContainerInWindow(this.$container);
};

scout.Popup.prototype._prefLocationWithAnchor = function(verticalAlignment, horizontalAlignment) {
  var $container = this.$container;
  horizontalAlignment = horizontalAlignment || this.horizontalAlignment;
  verticalAlignment = verticalAlignment || this.verticalAlignment;
  var anchorBounds = this.getAnchorBounds();
  var size = scout.graphics.size($container);
  var margins = scout.graphics.margins($container);
  var Alignment = scout.Popup.Alignment;
  var arrowBounds = this.$arrow ? scout.graphics.bounds(this.$arrow) : null;

  $container.removeClass(this._alignClasses());
  $container.addClass(verticalAlignment + ' ' + horizontalAlignment);

  var widthWithMargin = size.width + margins.horizontal();
  var width = size.width;
  var x = anchorBounds.x;
  if (horizontalAlignment === Alignment.LEFT) {
    x -= widthWithMargin;
  } else if (horizontalAlignment === Alignment.LEFTEDGE) {
    if (this.withArrow) {
      x += anchorBounds.width / 2 - arrowBounds.center().x - margins.left;
    } else {
      x = anchorBounds.x - margins.left;
    }
  } else if (horizontalAlignment === Alignment.CENTER) {
    x += anchorBounds.width / 2 - width / 2 - margins.left;
  } else if (horizontalAlignment === Alignment.RIGHT) {
    x += anchorBounds.width;
  } else if (horizontalAlignment === Alignment.RIGHTEDGE) {
    if (this.withArrow) {
      x += anchorBounds.width / 2 - arrowBounds.center().x - margins.right;
    } else {
      x = anchorBounds.x + anchorBounds.width - width - margins.right;
    }
  }

  var heightWithMargin = size.height + margins.vertical();
  var height = size.height;
  var y = anchorBounds.y;
  if (verticalAlignment === Alignment.TOP) {
    y -= heightWithMargin;
  } else if (verticalAlignment === Alignment.TOPEDGE) {
    if (this.withArrow) {
      y += anchorBounds.height / 2 - arrowBounds.center().y - margins.top;
    } else {
      y = anchorBounds.y - margins.top;
    }
  } else if (verticalAlignment === Alignment.CENTER) {
    y += anchorBounds.height / 2 - height / 2 - margins.top;
  } else if (verticalAlignment === Alignment.BOTTOM) {
    y += anchorBounds.height;
  } else if (verticalAlignment === Alignment.BOTTOMEDGE) {
    if (this.withArrow) {
      y += anchorBounds.height / 2 - arrowBounds.center().y - margins.bottom;
    } else {
      y = anchorBounds.y + anchorBounds.height - height - margins.bottom;
    }
  }

  // this.$parent might not be at (0,0) of the document
  var parentOffset = this.$parent.offset();
  x -= parentOffset.left;
  y -= parentOffset.top;

  return new scout.Point(x,y);
};

scout.Popup.prototype._alignClasses = function() {
  var Alignment = scout.Popup.Alignment;
  return scout.strings.join(' ', Alignment.LEFT, Alignment.LEFTEDGE, Alignment.CENTER, Alignment.RIGHT, Alignment.RIGHTEDGE,
    Alignment.TOP, Alignment.TOPEDGE, Alignment.CENTER, Alignment.BOTTOM, Alignment.BOTTOMEDGE);
};

scout.Popup.prototype.getAnchorBounds = function() {
  var anchorBounds = this.anchorBounds;
  if (!anchorBounds) {
    anchorBounds = scout.graphics.offsetBounds(this.$anchor, {
      exact: true
    });
  }
  return anchorBounds;
};

scout.Popup.prototype.getWindowSize = function() {
  var $window = this.$parent.window();
  return new scout.Dimension($window.width(), $window.height());
};

scout.Popup.prototype.overlap = function(location, includeMargin) {
  var $container = this.$container;
  if (!$container || !location) {
    return;
  }
  includeMargin = scout.nvl(includeMargin, true);
  var overlapX, overlapY,
    height = $container.outerHeight(includeMargin),
    width = $container.outerWidth(includeMargin),
    left = location.x,
    top = location.y;

  overlapX = left + width + this.windowPaddingX - $container.entryPoint().outerWidth(true);
  overlapY = top + height + this.windowPaddingY - $container.entryPoint().outerHeight(true);
  return new scout.Point(overlapX, overlapY);
};

scout.Popup.prototype.adjustLocation = function(location, switchIfNecessary) {
  var verticalAlignment = this.verticalAlignment,
    horizontalAlignment = this.horizontalAlignment,
    overlap = this.overlap(location);

  location = location.clone();
  if (overlap.y > 0) {
    var verticalSwitch = scout.nvl(switchIfNecessary, this.verticalSwitch);
    if (verticalSwitch) {
      // Switch vertical alignment
      verticalAlignment = scout.Popup.SwitchRule[verticalAlignment];

      // Ensure arrow has correct class and margin added by moving logic is removed
      this._updateArrowClass(verticalAlignment);
      if (this.$arrow) {
        this.$arrow.cssMarginTop(null);
      }

      location = this.prefLocation(verticalAlignment);
    } else {
      // Move popup to the top until it gets fully visible (if switch is disabled)
      location.y -= overlap.y;

      // Also move arrow so that it still points to the center of the anchor
      if (this.$arrow && (this.$arrow.hasClass(scout.Popup.Alignment.LEFT) || this.$arrow.hasClass(scout.Popup.Alignment.RIGHT))) {
        this.$arrow.cssMarginTop(overlap.y);
      }
    }
  }
  if (overlap.x > 0) {
    var horizontalSwitch = scout.nvl(switchIfNecessary, this.horizontalSwitch);
    if (horizontalSwitch) {
      // Switch horizontal alignment
      horizontalAlignment = scout.Popup.SwitchRule[horizontalAlignment];

      // Ensure arrow has correct class and margin added by moving logic is removed
      this._updateArrowClass(verticalAlignment, horizontalAlignment);
      if (this.$arrow) {
        this.$arrow.cssMarginLeft(null);
      }

      location = this.prefLocation(verticalAlignment, horizontalAlignment);
    } else {
      // Move popup to the left until it gets fully visible (if switch is disabled)
      location.x -= overlap.x;

      // Also move arrow so that it still points to the center of the anchor
      if (this.$arrow && (this.$arrow.hasClass(scout.Popup.Alignment.TOP) || this.$arrow.hasClass(scout.Popup.Alignment.BOTTOM))) {
        this.$arrow.cssMarginLeft(overlap.x);
      }
    }
  }
  return location;
};

scout.Popup.prototype.size = function() {
  var size = this.prefSize(this.$container);
  if (!size) {
    return;
  }
  scout.graphics.setSize(this.$container, size);
};

scout.Popup.prototype.prefSize = function($container) {
  return null;
};

scout.Popup.prototype.position = function(switchIfNecessary) {
  this._validateVisibility();
  this._position(switchIfNecessary);
};

scout.Popup.prototype._position = function(switchIfNecessary) {
  var location = this.prefLocation();
  if (!location) {
    return;
  }
  location = this.adjustLocation(location, switchIfNecessary);
  this.setLocation(location);
};

scout.Popup.prototype.setLocation = function(location) {
  this.$container
    .css('left', location.x)
    .css('top', location.y);
  this._triggerLocationChange();
};

/**
 * Popups with an anchor must only be visible if the anchor is in view (prevents that the popup points at an invisible anchor)
 */
scout.Popup.prototype._validateVisibility = function() {
  if (!this.boundToAnchor || !this.$anchor) {
    return;
  }
  var inView = this._isInView();
  var needsLayouting = this.$container.isVisible() !== inView && inView;
  this.$container.toggleClass('invisible', !inView); // Use visibility: hidden to not break layouting / size measurement
  if (needsLayouting) {
    this.revalidateLayout();
  }
};

scout.Popup.prototype._isInView = function() {
  if (!this.boundToAnchor || !this.$anchor) {
    return;
  }
  var anchorBounds = this.getAnchorBounds();
  return scout.scrollbars.isLocationInView(anchorBounds.center(), this.$anchor.scrollParent());
};

scout.Popup.prototype._triggerLocationChange = function() {
  this.trigger('locationChange');
};

/**
 * Fire event that this popup is about to open.
 */
scout.Popup.prototype._triggerPopupOpenEvent = function() {
  this.session.desktop.trigger('popupOpen', {
    popup: this
  });
};

scout.Popup.prototype.belongsTo = function($anchor) {
  return this.$anchor[0] === $anchor[0];
};

scout.Popup.prototype.set$Anchor = function($anchor) {
  if (this.$anchor) {
    this._detachAnchorScrollHandler();
  }
  this.setProperty('$anchor', $anchor);
  if (this.rendered) {
    this._attachAnchorScrollHandler();
    this.revalidateLayout();
    this.position();
  }
};

scout.Popup.prototype.isOpen = function() {
  return this.rendered;
};

scout.Popup.prototype.ensureOpen = function() {
  if (!this.isOpen()) {
    this.open();
  }
};
