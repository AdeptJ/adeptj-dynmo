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

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration.Dynamic;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * FrameworkBootstrap that handles the OSGi Framework startup and shutdown.
 *
 * @author Rakesh.Kumar, AdeptJ
 */
public enum FrameworkBootstrap {

    INSTANCE;

    public static final String DISPATCHER = "DispatcherProxyServlet";

	public static final String ROOT_MAPPING = "/*";

	private static final Logger LOGGER = LoggerFactory.getLogger(FrameworkBootstrap.class);

    private Framework framework;

    private FrameworkRestartHandler listener;

    private BundleContext systemBundleContext;

    public void startFramework() {
        try {
            this.framework = this.createFramework();
            this.framework.start();
            this.systemBundleContext = this.framework.getBundleContext();
            FrameworkServlet frameworkServlet = new FrameworkServlet();
            this.listener = new FrameworkRestartHandler(frameworkServlet);
            this.systemBundleContext.addFrameworkListener(this.listener);
            // Set the BundleContext as a ServletContext attribute as per Felix HttpBridge Specification.
            ServletContext context = ServletContextAware.INSTANCE.getServletContext();
            context.setAttribute(BundleContext.class.getName(), this.systemBundleContext);
            BundleProvisioner.INSTANCE.provisionBundles(this.systemBundleContext);
            LOGGER.info("OSGi Framework Started!!");
            /*
			 * Register the FrameworkServlet after the OSGi Framework started successfully.
			 * This will ensure that the Felix {@link DispatcherServlet} is available as an OSGi service and can be tracked. 
			 * {@link FrameworkServlet} collect the DispatcherServlet service and delegates all the service calls to it.
			 */
            Dynamic registration = context.addServlet(DISPATCHER, frameworkServlet);
            registration.setLoadOnStartup(1);
            registration.addMapping(ROOT_MAPPING);
            LOGGER.info("DispatcherProxyServlet registered successfully!!");
        } catch (Exception ex) {
            LOGGER.error("Failed to start OSGi Framework!!", ex);
            // Stop the Framework if the BundleProvisioner throws exception.
            this.stopFramework();
        }
    }
    
    protected void setSystemBundleContext(BundleContext bundleContext) {
    	this.systemBundleContext = bundleContext;
    }

    public void stopFramework() {
        try {
        	if (this.framework != null && this.systemBundleContext != null) {
        		this.systemBundleContext.removeFrameworkListener(this.listener);
                this.framework.stop();
                // A value of zero will wait indefinitely.
                FrameworkEvent event = this.framework.waitForStop(0);
                LOGGER.info("OSGi Framework Stopped, Event Code: [{}]", event.getType());
        	} else {
        		LOGGER.info("OSGi Framework not started yet, nothing to stop!!");
        	}
        } catch (Exception ex) {
            LOGGER.error("Error Stopping OSGi Framework!!", ex);
        }
    }

	private Framework createFramework() throws Exception {
		Framework framework = null;
		for (FrameworkFactory factory : ServiceLoader.load(FrameworkFactory.class, this.getClass().getClassLoader())) {
			framework = factory.newFramework(this.createFrameworkConfigs());
			// Ideally there will only be a single FrameworkFactory.
			break;
		}
		return framework;
	}

    private Map<String, String> createFrameworkConfigs() throws Exception {
        Properties props = new Properties();
        props.load(FrameworkBootstrap.class.getResourceAsStream("/framework.properties"));
        Map<String, String> configs = new HashMap<>();
        props.forEach((key, val) -> {
        	configs.put((String) key, (String) val);
        });
		/*
		 * WARNING: Only set this property if absolutely needed.
		 * (for example you implement an HttpSessionListener and want to access {@ link ServletContext}
		 * attributes of the ServletContext to which the HttpSession is linked). Otherwise leave this property unset.
		 */
        configs.put("org.apache.felix.http.shared_servlet_context_attributes", "true");
        String frameworkArtifactsDir = System.getProperty("user.dir") + "/modularweb-micro";
        configs.put("org.osgi.framework.storage", frameworkArtifactsDir + "/felix");
        configs.put("felix.cm.dir", frameworkArtifactsDir + "/osgi-config");
        configs.put("org.osgi.framework.bundle.parent", "framework");
        // set felix.log.level debug
        // configs.put("felix.log.level", "4");
        /*
         * WARNING: This breaks OSGi Modularity, But EhCache won't work without this.
         * Declaring on Sun specific classes only.
         */
        configs.put("org.osgi.framework.bootdelegation", "com.yourkit.*,sun.*,com.sun.*");
		/*
		 * Register the OsgiManager HttpServlet using the prefix "/" so that it could be resolved by Felix DispatcherServlet
		 * which is registered on "/" itself. This is optional as "/system/console" is default.
		 */
        configs.put("felix.webconsole.manager.root", "/system/console");
        LOGGER.debug("OSGi Framework Configurations: {}", configs);
        return configs;
    }
}