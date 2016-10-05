/* 
 * =============================================================================
 * 
 * Copyright (c) 2016 AdeptJ
 * Copyright (c) 2016 Rakesh Kumar <irakeshk@outlook.com>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * =============================================================================
 */
package com.adeptj.modularweb.micro.osgi;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * BridgeHttpSessionListener.
 *
 * @author Rakesh.Kumar, AdeptJ.
 */
public class BridgeHttpSessionListener implements HttpSessionListener {

	@Override
	public void sessionCreated(HttpSessionEvent se) {
		final HttpSessionListener sessionDispatcher = getHttpSessionListener();
		if (sessionDispatcher != null) {
			sessionDispatcher.sessionCreated(se);
		}
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent se) {
		final HttpSessionListener sessionDispatcher = getHttpSessionListener();
		if (sessionDispatcher != null) {
			sessionDispatcher.sessionDestroyed(se);
		}
	}

	private HttpSessionListener getHttpSessionListener() {
		final EventDispatcherTracker tracker = EventDispatcherTrackerSupport.INSTANCE.getEventDispatcherTracker();
		if (tracker != null) {
			return tracker.getHttpSessionListener();
		}
		return null;
	}
}
