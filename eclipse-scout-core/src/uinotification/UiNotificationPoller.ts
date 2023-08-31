/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
import {ajax, AjaxCall, AjaxError, App, BackgroundJobPollingStatus, PropertyEventEmitter, scout, UiNotificationDo} from '../index';

let instance: UiNotificationPoller;

export class UiNotificationPoller extends PropertyEventEmitter {
  broadcastChannel: BroadcastChannel;
  longPolling: boolean;
  requestTimeout: number;
  shortPollInterval: number;
  status: BackgroundJobPollingStatus;
  topics: string[];
  lastId: number;
  url: string;
  dispatcher: (notifications: UiNotificationDo[]) => void;
  protected _call: AjaxCall;
  protected _pollCounter: number; // number of pollers to the same domain in the browser over all tabs
  protected _broadCastChannelHandler: (event: MessageEvent) => void;
  protected _documentVisibilityChangeHandler: (event: Event) => void;
  constructor() {
    super();
    this.requestTimeout = 75000;
    this.shortPollInterval = 10000;
    this.broadcastChannel = new BroadcastChannel('ui-notification-poller');
    this._broadCastChannelHandler = event => this._onBroadcastMessage(event);
    this._documentVisibilityChangeHandler = event => this._onDocumentVisibilityChange(event);
    this._pollCounter = 0;
    this._updateLongPolling();
    this.lastId = -1;
  }

  setTopics(topics: string[]) {
    this.topics = topics;
  }

  setDispatcher(dispatcher: (notifications: UiNotificationDo[]) => void) {
    this.dispatcher = dispatcher;
  }

  start() {
    if (this.status === BackgroundJobPollingStatus.RUNNING) {
      return;
    }
    this.broadcastChannel.addEventListener('message', this._broadCastChannelHandler);
    document.addEventListener('visibilitychange', this._documentVisibilityChangeHandler);
    this.poll();
  }

  stop() {
    if (this.status !== BackgroundJobPollingStatus.RUNNING) {
      return;
    }
    this.broadcastChannel.removeEventListener('message', this._broadCastChannelHandler);
    document.removeEventListener('visibilitychange', this._documentVisibilityChangeHandler);
    this._call?.abort();
    this.setStatus(BackgroundJobPollingStatus.STOPPED);
  }

  poll() {
    this._poll();

    this.setStatus(BackgroundJobPollingStatus.RUNNING);
  }

  protected _poll() {
    this._call = ajax.createCallJson({
      url: this.url,
      timeout: this.requestTimeout,
      data: JSON.stringify({
        longPolling: this.longPolling,
        topics: this.topics,
        lastId: this.lastId
      })
    });
    this._call.call()
      .then((response: UiNotificationResponse) => this._onSuccess(response))
      .catch(error => this._onError(error));
  }

  protected _onSuccess(response: UiNotificationResponse) {
    // TODO CGU CN handle response.error, response.sessionTerminated ?
    console.log('UI notification received: ' + JSON.stringify(response));
    const notifications = response.notifications || [];
    this.lastId = notifications.reduce((lastId: number, notification: UiNotificationDo) => Math.max(lastId, notification.id), this.lastId);
    if (this.dispatcher) {
      this.dispatcher(notifications);
    }

    setTimeout(() => this.poll(), this.longPolling ? 0 : this.shortPollInterval);
  }

  protected _onError(error: AjaxError) {
    this.setStatus(BackgroundJobPollingStatus.FAILURE);
    console.log(error);
    // TODO CGU CN Handle offline error, delegate to handler? Resume poller when going online after network loss? How to handle it for Scout JS only case?
  }

  setLongPolling(longPolling: boolean) {
    if (this.setProperty('longPolling', longPolling)) {
      if (longPolling) {
        console.log('Switched to long polling');
      } else {
        console.log('Switched to short polling');
      }
    }
  }

  protected _updateLongPolling() {
    // TODO CGU is this reliable with all browsers (ios) ?
    if (document.visibilityState === 'hidden' && this._pollCounter >= 1) {
      this.setLongPolling(false);
    } else {
      this.setLongPolling(true);
    }
  }

  setStatus(status: BackgroundJobPollingStatus) {
    const changed = this.setProperty('status', status);
    if (changed) {
      this.broadcastChannel.postMessage({
        status: this.status,
        url: this.url
      });
      console.log('UI notification poller status changed: ' + status);
    }
    // $.log.isInfoEnabled() && $.log.info('UI notification poller status changed: ' + status);
  }

  protected _onBroadcastMessage(event: MessageEvent) {
    console.log('Broadcast event received: ' + JSON.stringify(event.data));
    let status = event.data?.status as BackgroundJobPollingStatus;
    let url = event.data?.url;
    if (url !== this.url) {
      return;
    }
    if (status === BackgroundJobPollingStatus.RUNNING) {
      this._pollCounter++;
    } else if (this._pollCounter > 0) {
      this._pollCounter--;
    }
    this._updateLongPolling();
  }

  protected _onDocumentVisibilityChange(event: Event) {
    console.log('Document visibility changed: ' + document.visibilityState);
    this._updateLongPolling();
  }

  static get(): UiNotificationPoller {
    if (!instance) {
      instance = scout.create(UiNotificationPoller);
    }
    return instance;
  }
}

export interface UiNotificationResponse {
  notifications: UiNotificationDo[];
}
