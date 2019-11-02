/*
 * Copyright (c) 2014-2017 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the BSI CRM Software License v1.0
 * which accompanies this distribution as bsi-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
import {scout, strings} from '@eclipse-scout/core';
import {AbstractCircleChartRenderer, Chart} from '../index';
import * as $ from 'jquery';

export default class PieChartRenderer extends AbstractCircleChartRenderer {

  constructor(chart) {
    super(chart);
    this.segmentSelectorForAnimation = '.pie-chart';
    this.centerCircleR = 0;
    this.wireLegendUsedSpace = 25;
    this.minHeightForWireLegend = 100;
  }


  _validate() {
    var chartData = this.chart.chartData;
    if (chartData.axes.length > 0 || chartData.chartValueGroups.length === 0) {
      return false;
    }
    return true;
  }

  _render() {
    var that = this,
      sum = this._sumValues(this.chart.chartData.chartValueGroups),
      segments = this._createSegments(),
      startAngle = 0,
      endAngle = 0;

    this.r = Math.min(this.chartBox.height, this.chartBox.width) / 2;
    var tweenInFunc = function(now, fx) {
      var $this = $(this);
      var start = $this.data('animation-start');
      var end = $this.data('animation-end');
      $this.attr('d', that.pathSegment(start * fx.pos, end * fx.pos));
    };

    // t = segment index
    for (var t = 0; t < segments.length; t++) {
      var segment = segments[t];
      endAngle = startAngle + segment.value / sum;

      // -0.00001, else: only 1 arc is not drawn, svg...
      if (endAngle === 1) {
        endAngle = endAngle - 0.00001;
      }

      // arc segment
      var colorClass = 'color' + t,
        arcClass = 'pie-chart';
      if (this.chart.autoColor) {
        arcClass += ' auto-color ' + colorClass;
      } else if (segment.cssClass) {
        arcClass += ' ' + segment.cssClass;
      }
      var clickable = this.chart.clickable && segment.clickable;
      if (clickable) {
        arcClass += ' clickable';
      }

      var $arc = this.$svg.appendSVG('path', arcClass)
        .data('animation-start', startAngle)
        .data('animation-end', endAngle);
      if (this.animated) {
        $arc
          .delay(200)
          .animate({
            tabIndex: 0
          }, this._createAnimationObjectWithTabindexRemoval(tweenInFunc));
      } else {
        $arc.attr('d', this.pathSegment(startAngle, endAngle));
      }
      if (clickable) {
        $arc.on('click', this._createClickObject(-1, -1, segment.valueGroupIndex), this.chart._onValueClick.bind(this.chart));
      }
      if (!this.chart.autoColor && !segment.cssClass) {
        $arc.attr('fill', segment.color);
      }

      var legendClass = 'pie-chart';
      if (this.chart.autoColor) {
        legendClass += ' auto-color ' + colorClass;
      } else if (segment.cssClass) {
        legendClass += ' ' + segment.cssClass;
      }
      this._renderLegendEntry(segment.label, (!this.chart.autoColor ? segment.color : null), legendClass, t);

      // data inside the arc
      var midPoint = (startAngle + (endAngle - startAngle) / 2) * 2 * Math.PI,
        percentage = Math.round(segment.value / sum * 100) + '%';
      if (endAngle - startAngle >= 0.05) {
        this._renderPieChartPercentage(midPoint, percentage);
      }

      startAngle = endAngle;
      this._addWireLabels($arc, midPoint, segment.label, percentage);
    }
    if (this.centerCircleR > 0) {
      this.$svg.appendSVG('circle', 'innerCircle')
        .attr('cx', this.chartBox.mX())
        .attr('cy', this.chartBox.mY())
        .attr('r', this.centerCircleR);
    }
  }

  _renderPieChartPercentage(midPoint, percentage) {
    var labelR = this.centerCircleR > 0 ? ((this.r - this.centerCircleR) / 2) + this.centerCircleR : this.r / 2 + this.r / 10,
      labelXPosition = this.chartBox.mX() + labelR * Math.sin(midPoint),
      labelYPosition = this.chartBox.mY() - labelR * Math.cos(midPoint);

    var $label2 = this.$svg.appendSVG('text', 'pie-chart-percentage ')
      .attr('x', labelXPosition)
      .attr('y', labelYPosition)
      .text(percentage);
    if (this.animated) {
      $label2
        .attr('opacity', 0)
        .delay(600)
        .animateSVG('opacity', 1, 300, null, true);
    }
  }

  _calcChartBoxHeight() {
    var height = super._calcChartBoxHeight();
    this.isBiggerThanMinHeight = this.minHeightForWireLegend < height;
    return this.chart.interactiveLegendVisible ? this.isBiggerThanMinHeight ? height - this.wireLegendUsedSpace / 2 : height - 2 * this.wireLegendUsedSpace : height;
  }

  _calcChartBoxYOffset() {
    var yOffset = super._calcChartBoxYOffset();
    return this.chart.interactiveLegendVisible ? this.isBiggerThanMinHeight ? yOffset + this.wireLegendUsedSpace / 4 : yOffset + this.wireLegendUsedSpace : yOffset;
  }

  _initLabelBox() {
    if (!this.chart.legendVisible || this.suppressLegendBox) {
      return;
    }
    super._initLabelBox();
    if (this.chart.interactiveLegendVisible && this.chart.legendPosition === Chart.LEGEND_POSITION_BOTTOM) {
      this.labelBox.y = this.isBiggerThanMinHeight ? this.labelBox.y + this.wireLegendUsedSpace / 2 : this.labelBox.y + 2 * this.wireLegendUsedSpace;
    }
  }

  _addWireLabels($arc, midPoint, label, percentage) {
    // add wire angle
    var wireLabelX1 = this.chartBox.mX() + this.r * Math.sin(midPoint),
      wireLabelY1 = this.chartBox.mY() - this.r * Math.cos(midPoint),
      wireLabledistanceToChart = this.isBiggerThanMinHeight ? 5 : 10,
      legendPositions = {
        x1: wireLabelX1,
        x2: wireLabelX1 > this.chartBox.mX() ? wireLabelX1 - 20 : wireLabelX1 + 20,
        y1: wireLabelY1,
        y2: wireLabelY1 > this.chartBox.mY() ? this.chartBox.mY() + this.r + wireLabledistanceToChart : this.chartBox.mY() - this.r - wireLabledistanceToChart,
        v: wireLabelY1 > this.chartBox.mY() ? 1 : -1,
        h: wireLabelX1 > this.chartBox.mX() ? -1 : 1
      };
    var legend = this._renderWireLegend(
      strings.join(': ', label, percentage),
      legendPositions, 'line-chart-wire-label', false);
    legend.detachFunc();
    $arc
      .mouseenter(function() {
        legend.attachFunc();
      })
      .mouseleave(function() {
        legend.detachFunc();
      });
  }

  _useFontSizeBig() {
    return (this.svgHeight / this.svgWidth) > 0.5 && this.svgWidth < 300;
  }

  _useFontSizeMiddle() {
    return (this.svgHeight / this.svgWidth) > 0.3 && this.svgWidth < 800 && !this._useFontSizeBig();
  }

  _useFontSizeSmall() {
    return (this.svgHeight / this.svgWidth) > 0.3 && this.svgWidth < 1200 && !this._useFontSizeMiddle();
  }

  _useFontSizeSmallest() {
    return !(this._useFontSizeBig() || this._useFontSizeMiddle() || this._useFontSizeSmall());
  }

  /**
   * returns ordered segments with label, value and color
   */
  _createSegments() {
    var valueGroups = this.chart.chartData.chartValueGroups;
    var segments = valueGroups.map(function(valueGroup, index) {
      return scout.create('PieSegment', {
        label: valueGroup.groupName,
        value: valueGroup.values[0],
        color: this.chart.autoColor ? 'auto' : valueGroup.colorHexValue,
        cssClass: valueGroup.cssClass,
        clickable: valueGroup.clickable,
        valueGroup: valueGroup,
        valueGroupIndex: index // must keep the original group-index for callback to server
      });
    }.bind(this))
      .sort(this._sortSegments);

    // Collect small segments in an "others" segment
    // Note: this is pure UI logic, there's also some server logic that creates an "other" valueGroup
    // this may lead to cases where we have two "other" segments in the chart. I guess this is not 100% correct
    var maxSegments = this.chart.maxSegments;
    if (segments.length > maxSegments) {
      for (var i = segments.length - 1; i >= maxSegments; i--) {
        segments[maxSegments - 1].value += segments[i].value;
        segments.pop();
      }
      var others = segments[maxSegments - 1];
      others.clickable = false;
      others.label = this.chart.session.text('ui.OtherValues');
    }

    return segments;
  }

  _sortSegments(a, b) {
    return b.value - a.value;
  }

  _sumValues(groups) {
    var sum = 0;
    for (var i = 0; i < groups.length; i++) {
      sum += Number(groups[i].values[0]);
    }
    return sum;
  }

  _removeAnimated(afterRemoveFunc) {
    if (this.animationTriggered) {
      return;
    }
    this.$svg.children('.pie-chart-percentage').remove();
    super._removeAnimated(afterRemoveFunc);
  }
}
