/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
import {ajax, AjaxCall, AjaxError, App, BackgroundJobPollingStatus, DoEntity, PropertyEventEmitter, scout} from '../index';

let instance: ClientNotificationPoller;

export class ClientNotificationPoller extends PropertyEventEmitter {
  broadcastChannel: BroadcastChannel;
  longPolling: boolean;
  requestTimeout: number;
  shortPollInterval: number;
  status: BackgroundJobPollingStatus;
  sse: boolean;
  protected _call: AjaxCall;
  protected _sse: EventSource;
  protected _pollCounter: number; // number of pollers to the same domain in the browser over all tabs

  constructor() {
    super();
    this.sse = true;
    this.requestTimeout = 75000;
    this.broadcastChannel = new BroadcastChannel('client-notification-poller');
    this.broadcastChannel.addEventListener('message', event => this._onBroadcastMessage(event));
    this._pollCounter = 0;
    document.addEventListener('visibilitychange', event => this._onDocumentVisibilityChange(event));
    this._updateLongPolling();
  }

  start() {
    if (this.status === BackgroundJobPollingStatus.RUNNING) {
      return;
    }
    this.poll();
  }

  stop() {
    if (this.status !== BackgroundJobPollingStatus.RUNNING) {
      return;
    }
    this._call?.abort();
    this._sse?.close();
    this.setStatus(BackgroundJobPollingStatus.STOPPED);
  }

  poll() {
    if (this.sse) {
      this._pollSse();
    } else {
      this._poll();
    }

    this.setStatus(BackgroundJobPollingStatus.RUNNING);
  }

  protected _poll() {
    this._call = ajax.createCallJson({
      url: 'poll',
      timeout: this.requestTimeout,
      data: {
        longPolling: this.longPolling
      }
    });
    this._call.call()
      .then((response: ClientNotificationResponse) => this._onSuccess(response))
      .catch(error => this._onError(error));
  }

  protected _pollSse() {
    this._sse = new EventSource('sse?short=' + !this.longPolling, {
      withCredentials: true
    });
    this._sse.addEventListener('message', event => {
      console.log('UI notification received via SSE: ' + event.data);
      // setTimeout(() => this.poll(), this.longPolling ? 0 : this.shortPollInterval);
    });
    this._sse.addEventListener('error', event => {
      console.log(this._sse.readyState);
      if (this._sse.readyState === 0) {
        this.stop();
        this.poll();
      }
    });
  }

  protected _onSuccess(response: ClientNotificationResponse) {
    // TODO CGU CN handle response.error, response.sessionTerminated ?
    console.log('UI notification received: ' + response);
    setTimeout(() => this.poll(), this.longPolling ? 0 : this.shortPollInterval);
  }

  protected _onError(error: AjaxError) {
    this.setStatus(BackgroundJobPollingStatus.FAILURE);
    console.log(error);
    // TODO CGU CN Handle offline error, delegate to handler? Resume poller when going online after network loss? How to handle it for Scout JS only case?
  }

  setLongPolling(longPolling: boolean) {
    this.setProperty('longPolling', longPolling);
  }

  protected _updateLongPolling() {
    if (document.visibilityState === 'hidden' && this._pollCounter >= 1) {
      this.setLongPolling(false);
      console.log('Switched to short polling');
    } else {
      this.setLongPolling(true);
      console.log('Switched to long polling');
    }
  }

  setStatus(status: BackgroundJobPollingStatus) {
    this.setProperty('status', status);
    this.broadcastChannel.postMessage({
      status: this.status
    });
    console.log('UI notification poller status changed: ' + status);
    // $.log.isInfoEnabled() && $.log.info('UI notification poller status changed: ' + status);
  }

  protected _onBroadcastMessage(event: MessageEvent) {
    let status = event.data?.status as BackgroundJobPollingStatus;
    if (status === BackgroundJobPollingStatus.RUNNING) {
      this._pollCounter++;
    } else if (this._pollCounter > 0) {
      this._pollCounter--;
    }
    console.log('Broadcast event received: ' + JSON.stringify(event.data));
    this._updateLongPolling();
  }

  protected _onDocumentVisibilityChange(event: Event) {
    console.log('Document visibility changed: ' + document.visibilityState);
    this._updateLongPolling();
  }

  static get(): ClientNotificationPoller {
    if (!instance) {
      instance = scout.create(ClientNotificationPoller);
    }
    return instance;
  }
}

export interface ClientNotificationResponse {
  notification: DoEntity;
}

App.addListener('init', () => {
  // Start polling when application is ready
  ClientNotificationPoller.get().start();
  // TODO CGU CN stop on tab / app close or broadcast with a heartbeat instead because tab close is not reliable? (ios)
});
