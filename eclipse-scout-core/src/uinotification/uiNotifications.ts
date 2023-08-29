/*
 * Copyright (c) 2010, 2023 BSI Business Systems Integration AG
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
import {DoEntity} from '../dataobject/DoEntity';
import {UiNotificationPoller} from './UiNotificationPoller';
import {scout} from '../scout';
import {UiNotificationDo} from './UiNotificationDo';

export type UiNotificationHandler = (notification: DoEntity) => void;

let systems = new Map<string, System>();

export const uiNotifications = {
  subscribe(topic: string, handler: UiNotificationHandler, system?: string) {
    scout.assertParameter('topic', topic);
    scout.assertParameter('handler', handler);
    let systemObj = getOrInitSystem(system);
    let subscriptions = systemObj.subscriptions;
    if (!subscriptions.has(topic)) {
      subscriptions.set(topic, new Set<UiNotificationHandler>());
    }
    subscriptions.get(topic).add(handler);

    let poller = systemObj.poller;
    if (!poller) {
      poller = scout.create(UiNotificationPoller, {
        url: systemObj.url,
        dispatcher: systemObj.dispatch.bind(systemObj)
      });
      systemObj.poller = poller;
    }
    poller.setTopics(Array.from(subscriptions.keys()));
    poller.start();
  },

  unsubscribe(topic: string, handler: UiNotificationHandler, system?: string) {
    scout.assertParameter('topic', topic);
    scout.assertParameter('handler', handler);
    let systemObj = getOrInitSystem(system);
    let subscriptions = systemObj.subscriptions;
    let handlers = subscriptions.get(topic) || new Set<UiNotificationHandler>();
    handlers.delete(handler);
    if (handlers.size === 0) {
      subscriptions.clear();
      systemObj.poller.stop();
      systemObj.poller = null;
    }
  },

  registerSystem(name: string, url: string) {
    if (systems.has(name)) {
      throw new Error(`System ${name} is already registered.`);
    }
    systems.set(name, new System(url));
  },

  unregisterSystem(name: string) {
    let system = systems.get(name);
    if (!system) {
      return;
    }
    system.poller?.stop();
    systems.delete(name);
  }
};

function getOrInitSystem(system: string): System {
  if (!system && !systems.has('main')) {
    uiNotifications.registerSystem('main', 'api/ui-notifications');
  }

  let systemObj = systems.get(system || 'main');
  if (!systemObj) {
    throw new Error(`Unknown system ${system}`);
  }
  return systemObj;
}

class System {
  subscriptions: Map<string, Set<UiNotificationHandler>>;
  poller: UiNotificationPoller;
  url: string;

  constructor(url: string) {
    this.subscriptions = new Map<string, Set<UiNotificationHandler>>();
    this.url = url;
  }

  dispatch(notifications: UiNotificationDo[]) {
    for (const notification of notifications) {
      let handlers = this.subscriptions.get(notification.topic);
      if (!handlers) {
        $.log.isInfoEnabled() && $.log.info('Notification received but no subscribers registered');
        return;
      }
      for (const handler of handlers) {
        handler(notification.message);
      }
    }
  }
}
