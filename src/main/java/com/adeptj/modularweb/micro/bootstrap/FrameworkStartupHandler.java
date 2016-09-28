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
package com.adeptj.modularweb.micro.bootstrap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StartupHandler is a {@link javax.servlet.annotation.HandlesTypes} that handles the OSGi Framework startup.
 *
 * @author Rakesh.Kumar, AdeptJ
 */
@StartupOrder(1)
public class FrameworkStartupHandler implements StartupHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FrameworkStartupHandler.class);

    /**
     * This method will be called by the {@link FrameworkServletContainerInitializer} while application startup is in
     * progress.
     *
     * @param context
     * @throws ServletException
     */
    @Override
	public void onStartup(ServletContext context) throws ServletException {
		LOGGER.info("Starting the OSGi Framework!!");
		long startTime = System.currentTimeMillis();
		FrameworkBootstrap.INSTANCE.startFramework();
		LOGGER.info("OSGi Framework started in [{}] ms!!", (System.currentTimeMillis() - startTime));
	}
}