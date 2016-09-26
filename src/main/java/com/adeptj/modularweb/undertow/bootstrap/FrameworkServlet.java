/**
 *
 */
package com.adeptj.modularweb.undertow.bootstrap;

import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * HttpServlet acting as a front controller for all of the incoming requests and
 * delegates the actual service request to Felix DispatcherServlet
 *
 * @author Rakesh.Kumar, AdeptJ
 */
public class FrameworkServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrameworkServlet.class);

    private static final long serialVersionUID = 702778293237417284L;

    private DispatcherServletTracker tracker;

    private static volatile boolean initialized;

    /**
     * Open the DispatcherServletTracker.
     */
    @Override
    public void init() throws ServletException {
        LOGGER.info("Initializing FrameworkServlet!!");
        if (!initialized) {
            try {
                this.tracker = new DispatcherServletTracker(ServletContextAware.INSTANCE.getBundleContext(),
                        this.getServletConfig());
                this.tracker.open();
                LOGGER.info("DispatcherServletTracker opened!!");
                initialized = true;
            } catch (InvalidSyntaxException ise) {
                LOGGER.error("Could not register the DispatcherServletTracker!!", ise);
                throw new ServletException("Could not register the DispatcherServletTracker!!", ise);
            }
        }
    }

    /**
     * Proxy for Felix DispatcherServlet, delegates all the calls to the underlying DispatcherServlet.
     */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        LOGGER.debug("Handling request: {}", req.getRequestURI());
        HttpServlet dispatcher = this.tracker.getDispatcher();
        try {
            if (dispatcher == null) {
            	LOGGER.warn("DispatcherServlet not ready yet!!");
            	res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            	return;
            } else {
                DispatcherType dispatcherType = req.getDispatcherType();
                LOGGER.debug("DispatcherType: {}", dispatcherType);
                if (DispatcherType.INCLUDE.equals(dispatcherType)) {
                    dispatcher.service(new IncludeHttpServletRequest(req), res);
                } else {
                    dispatcher.service(req, res);
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Exception while handling request!!", ex);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Close the DispatcherServletTracker.
     */
    @Override
    public void destroy() {
        LOGGER.info("Destroying FrameworkServlet!!");
        this.tracker.close();
        initialized = false;
        super.destroy();
    }

    public void disposeTracker() {
        initialized = false;
        this.tracker.close();
    }

    public boolean isInitialized() {
        return initialized;
    }

}