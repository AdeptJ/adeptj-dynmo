/*
###############################################################################
#                                                                             # 
#    Copyright 2016, AdeptJ (http://www.adeptj.com)                           #
#                                                                             #
#    Licensed under the Apache License, Version 2.0 (the "License");          #
#    you may not use this file except in compliance with the License.         #
#    You may obtain a copy of the License at                                  #
#                                                                             #
#        http://www.apache.org/licenses/LICENSE-2.0                           #
#                                                                             #
#    Unless required by applicable law or agreed to in writing, software      #
#    distributed under the License is distributed on an "AS IS" BASIS,        #
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. #
#    See the License for the specific language governing permissions and      #
#    limitations under the License.                                           #
#                                                                             #
###############################################################################
*/
package com.adeptj.runtime.osgi;

import com.adeptj.runtime.common.BundleContextHolder;
import com.adeptj.runtime.common.Environment;
import com.adeptj.runtime.common.IOUtils;
import com.adeptj.runtime.common.Times;
import com.adeptj.runtime.config.Configs;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import static com.adeptj.runtime.osgi.BundleProvisioner.CFG_KEY_FELIX_CM_DIR;
import static org.apache.felix.framework.util.FelixConstants.LOG_LEVEL_PROP;

/**
 * FrameworkManager: Handles the OSGi Framework(Apache Felix) lifecycle such as startup, shutdown etc.
 *
 * @author Rakesh.Kumar, AdeptJ
 */
public enum FrameworkManager {

    INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger(FrameworkManager.class);

    private static final String FRAMEWORK_PROPERTIES_CP_RESOURCE = "/framework.properties";

    private static final String FELIX_CM_DIR = "felix.cm.dir";

    private static final String MEM_DUMP_LOC = "felix.memoryusage.dump.location";

    private static final String CFG_KEY_MEM_DUMP_LOC = "memoryusage-dump-loc";

    private static final String LOGGER_CFG_FACTORY_FILTER = "(|(logger.names=*)(logger.level=*))";

    private static final String SYS_PROP_OVERWRITE_FRAMEWORK_CONF = "overwrite.framework.conf.file";

    private final static String FILE_INSTALL_DIR = "felix.fileinstall.dir";

    private Framework framework;

    private FrameworkLifecycleListener frameworkListener;

    private ServiceListener serviceListener;

    public void startFramework() {
        try {
            long startTime = System.nanoTime();
            LOGGER.info("Starting the OSGi Framework!!");
            Config felixConf = Configs.of().felix();
            FrameworkFactory frameworkFactory = ServiceLoader.load(FrameworkFactory.class).iterator().next();
            Map<String, String> frameworkConfigs = this.newFrameworkConfigs(felixConf);
            BundleContext bundleContext = this.initFramework(frameworkFactory, frameworkConfigs);
            boolean restartFramework = new BundleProvisioner().installUpdateBundles(felixConf, bundleContext);
            if (restartFramework) {
                LOGGER.info("Restarting OSGi Framework!");
                this.stopFramework();
                bundleContext = this.initFramework(frameworkFactory, frameworkConfigs);
            }
            BundleContextHolder.getInstance().setBundleContext(bundleContext);
            this.addListeners(bundleContext);
            this.framework.start();
            LOGGER.info("OSGi Framework [{} v{}] started in [{}] ms!!",
                    bundleContext.getBundle().getSymbolicName(),
                    bundleContext.getBundle().getVersion(), Times.elapsedMillis(startTime));
        } catch (Exception ex) { // NOSONAR
            LOGGER.error("Failed to start OSGi Framework!!", ex);
            // Stop the Framework if the Bundles throws exception.
            this.stopFramework();
        }
    }

    public void stopFramework() {
        try {
            if (this.framework == null) {
                LOGGER.warn("OSGi Framework not started yet, nothing to stop!!");
            } else {
                this.removeListeners();
                this.framework.stop();
                // A value of zero will wait indefinitely.
                FrameworkEvent event = this.framework.waitForStop(0);
                LOGGER.info("OSGi FrameworkEvent: [{}]", FrameworkEvents.asString(event.getType())); // NOSONAR
            }
        } catch (Exception ex) { // NOSONAR
            LOGGER.error("Error Stopping OSGi Framework!!", ex);
        }
    }

    private BundleContext initFramework(FrameworkFactory frameworkFactory,
                                        Map<String, String> frameworkConfigs) throws BundleException {
        this.framework = frameworkFactory.newFramework(frameworkConfigs);
        this.framework.init();
        return this.framework.getBundleContext();
    }

    private void addListeners(BundleContext bundleContext) throws InvalidSyntaxException {
        this.frameworkListener = new FrameworkLifecycleListener();
        bundleContext.addFrameworkListener(this.frameworkListener);
        this.serviceListener = new LoggerConfigFactoryListener();
        bundleContext.addServiceListener(this.serviceListener, LOGGER_CFG_FACTORY_FILTER);
    }

    private void removeListeners() {
        BundleContext bundleContext = BundleContextHolder.getInstance().getBundleContext();
        if (bundleContext != null) {
            try {
                bundleContext.removeServiceListener(this.serviceListener);
                LOGGER.info("Removing OSGi FrameworkListener!!");
                bundleContext.removeFrameworkListener(this.frameworkListener);
            } catch (Exception ex) { // NOSONAR
                LOGGER.error(ex.getMessage(), ex);
            }
        }
    }

    private Map<String, String> newFrameworkConfigs(Config felixConf) {
        Map<String, String> configs = this.loadFrameworkProperties();
        configs.put(FELIX_CM_DIR, felixConf.getString(CFG_KEY_FELIX_CM_DIR));
        configs.put(MEM_DUMP_LOC, felixConf.getString(CFG_KEY_MEM_DUMP_LOC));
        String felixLogLevel = System.getProperty(LOG_LEVEL_PROP);
        if (StringUtils.isNotEmpty(felixLogLevel)) {
            configs.put(LOG_LEVEL_PROP, felixLogLevel);
        }
        if (LOGGER.isDebugEnabled()) {
            this.printFrameworkConfigs(configs);
        }
        return configs;
    }

    private Map<String, String> loadFrameworkProperties() {
        Map<String, String> configs = new HashMap<>();
        Path confPath = Environment.getFrameworkConfPath();
        if (confPath.toFile().exists()) {
            if (Boolean.getBoolean(SYS_PROP_OVERWRITE_FRAMEWORK_CONF)) {
                this.createOrUpdateFrameworkPropertiesFile(configs, confPath);
            } else {
                try (InputStream stream = Files.newInputStream(confPath)) {
                    this.populateFrameworkConfigs(stream, configs);
                } catch (IOException ex) {
                    LOGGER.error("IOException while loading framework.properties from file system!!", ex);
                    // Fallback is try to load the classpath framework.properties
                    this.loadClasspathFrameworkProperties(configs);
                }
            }
        } else {
            this.createOrUpdateFrameworkPropertiesFile(configs, confPath);
            this.createFileInstallDirectory(configs.get(FILE_INSTALL_DIR));
        }
        return configs;
    }

    private void createFileInstallDirectory(String dir) {
        // Create the file install directory upfront otherwise we will run into issues.
        // https://issues.apache.org/jira/browse/FELIX-6393
        try {
            Files.createDirectories(Paths.get(dir));
        } catch (IOException ex) {
            // Gulp the exception as it should not stop framework bootstrapping.
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    private void loadClasspathFrameworkProperties(Map<String, String> configs) {
        try (InputStream stream = this.getClass().getResourceAsStream(FRAMEWORK_PROPERTIES_CP_RESOURCE)) {
            this.populateFrameworkConfigs(stream, configs);
        } catch (IOException exception) {
            LOGGER.error("IOException while loading framework.properties from classpath!!", exception);
        }
    }

    private void createOrUpdateFrameworkPropertiesFile(Map<String, String> configs, Path confPath) {
        try (InputStream stream = this.getClass().getResourceAsStream(FRAMEWORK_PROPERTIES_CP_RESOURCE)) {
            byte[] bytes = IOUtils.toBytes(stream);
            this.populateFrameworkConfigs(new ByteArrayInputStream(bytes), configs);
            Files.write(confPath, bytes);
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    private void populateFrameworkConfigs(InputStream stream, Map<String, String> configs) throws IOException {
        Properties props = new Properties();
        props.load(stream);
        for (String key : props.stringPropertyNames()) {
            configs.put(key, props.getProperty(key));
        }
    }

    private void printFrameworkConfigs(Map<String, String> configs) {
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, String>> iterator = configs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            builder.append(entry.getKey());
            builder.append('=').append('"');
            if (!StringUtils.startsWith(entry.getKey(), "crypto")) {
                builder.append(entry.getValue());
            }
            builder.append('"');
            if (iterator.hasNext()) {
                builder.append(',').append('\n');
            }
        }
        LOGGER.debug("OSGi Framework Configurations-\n{}", builder);
    }

    public static FrameworkManager getInstance() {
        return INSTANCE;
    }
}