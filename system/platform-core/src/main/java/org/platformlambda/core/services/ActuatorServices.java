package org.platformlambda.core.services;

import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.models.Kv;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.system.*;
import org.platformlambda.core.util.AppConfigReader;
import org.platformlambda.core.util.SimpleCache;
import org.platformlambda.core.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@PreLoad(route="actuator.services", instances=10)
public class ActuatorServices implements TypedLambdaFunction<EventEnvelope, Object> {
    private static final Logger log = LoggerFactory.getLogger(ActuatorServices.class);
    private static final Utility util = Utility.getInstance();
    private static final SimpleCache cache = SimpleCache.createCache("health.info", 5000);
    private static final String TYPE = "type";
    private static final String INFO = "info";
    private static final String ROUTES = "routes";
    private static final String LIB = "lib";
    private static final String ENV = "env";
    private static final String HEALTH = "health";
    private static final String HEALTH_STATUS = "health_status";
    private static final String SHUTDOWN = "shutdown";
    private static final String SUSPEND = "suspend";
    private static final String RESUME = "resume";
    private static final String LIVENESS_PROBE = "livenessprobe";
    private static final String USER = "user";
    private static final String WHEN = "when";
    private static final String ACCEPT = "accept";
    private static final String REQUIRED_SERVICES = "mandatory.health.dependencies";
    private static final String OPTIONAL_SERVICES = "optional.health.dependencies";
    private static final String ROUTE = "route";
    private static final String MESSAGE = "message";
    private static final String STATUS = "status";
    private static final String ORIGIN = "origin";
    private static final String NAME = "name";
    private static final String STATUS_CODE = "status_code";
    private static final String REQUIRED = "required";
    private static final String DEPENDENCY = "dependency";
    private static final String NOT_FOUND = "not found";
    private static final String PLEASE_CHECK = "Please check - ";
    private static final String CONTENT_TYPE = "content-type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_XML = "application/xml";
    private static final String APP_DESCRIPTION = "info.app.description";
    private static final String JAVA_VERSION = "java.version";
    private static final String JAVA_VM_VERSION = "java.vm.version";
    private static final String JAVA_RUNTIME_VERSION = "java.runtime.version";
    private static final String QUERY = "query";
    private static final String APP = "app";
    private static final String VERSION = "version";
    private static final String DESCRIPTION = "description";
    private static final String JVM = "vm";
    private static final String MEMORY = "memory";
    private static final String MAX = "max";
    private static final String ALLOCATED = "allocated";
    private static final String USED = "used";
    private static final String FREE = "free";
    private static final String INSTANCE = "instance";
    private static final String PERSONALITY = "personality";
    private static final String ROUTING = "routing";
    private static final String DOWNLOAD = "download";
    private static final String CLOUD_CONNECTOR = "cloud.connector";
    private static final String LIBRARY = "library";
    private static final String ROUTE_SUBSTITUTION = "route_substitution";
    private static final String TIME = "time";
    private static final String START = "start";
    private static final String CURRENT = "current";
    private static final String UP_TIME = "uptime";
    private static final String SHOW_ENV = "show.env.variables";
    private static final String SHOW_PROPERTIES = "show.application.properties";
    private static final String SYSTEM_ENV = "environment";
    private static final String APP_PROPS = "properties";
    private static final String MISSING = "missing";
    private static final String JOURNAL = "journal";
    private static final String STREAMS = "streams";
    private static final String ADDITIONAL_INFO = "additional.info";
    private static final String ERROR_FETCHING_INFO = "Unable to check additional.info - ";
    private static final AtomicBoolean healthStatus = new AtomicBoolean(true);
    private final List<String> requiredServices;
    private final List<String> optionalServices;
    private final String appDescription;
    private final Boolean isServiceMonitor;
    private final Boolean hasCloudConnector;

    public ActuatorServices() {
        var config = AppConfigReader.getInstance();
        appDescription = config.getProperty(APP_DESCRIPTION, Platform.getInstance().getName());
        isServiceMonitor = "true".equalsIgnoreCase(config.getProperty("service.monitor", "false"));
        hasCloudConnector = !"none".equals(config.getProperty(EventEmitter.CLOUD_CONNECTOR, "none"));
        requiredServices = util.split(config.getProperty(REQUIRED_SERVICES, ""), ", ");
        optionalServices = util.split(config.getProperty(OPTIONAL_SERVICES, ""), ", ");
        if (!requiredServices.isEmpty()) {
            log.info("Mandatory service dependencies - {}", requiredServices);
        }
        if (!optionalServices.isEmpty()) {
            log.info("Optional services dependencies - {}", optionalServices);
        }
    }
    
    @Override
    public Object handleEvent(Map<String, String> headers, EventEnvelope input, int instance) throws Exception {
        if (headers.containsKey(TYPE)) {
            var type = headers.get(TYPE);
            if (HEALTH_STATUS.equals(type) && input.getBody() instanceof Boolean healthState) {
                healthStatus.set(healthState);
                return true;
            }
            if (LIVENESS_PROBE.equals(type)) {
                if (healthStatus.get()) {
                    return new EventEnvelope().setBody("OK").setHeader("content-type", "text/plain");
                } else {
                    return new EventEnvelope().setBody("Unhealthy. Please check '/health' endpoint.")
                                            .setStatus(400).setHeader("content-type", "text/plain");
                }
            }
            if (HEALTH.equals(type)) {
                return handleHealth(headers);
            }
            if (INFO.equals(type) || LIB.equals(type) || ROUTES.equals(type) || ENV.equals(type)) {
                return handleInfo(headers);
            }
            if (headers.containsKey(USER)) {
                if (SUSPEND.equals(type) || RESUME.equals(type)) {
                    if (headers.containsKey(TYPE) && headers.containsKey(WHEN)) {
                        try {
                            EventEmitter.getInstance().send(ServiceDiscovery.SERVICE_REGISTRY,
                                            new Kv(TYPE, headers.get(TYPE)), new Kv(WHEN, headers.get(WHEN)),
                                            new Kv(USER, headers.get(USER)));
                        } catch (IOException e) {
                            log.error("Unable to perform {} - {}", type, e.getMessage());
                        }
                    }
                }
                if (SHUTDOWN.equals(type) && headers.containsKey(USER)) {
                    log.info("Shutdown requested by {}", headers.get(USER));
                    System.exit(-2);
                }
            }
        }
        return false;
    }

    private Object handleInfo(Map<String, String> headers) {
        var platform = Platform.getInstance();
        var po = EventEmitter.getInstance();
        var acceptHeader = headers.getOrDefault(ACCEPT, "?");
        var accept = acceptHeader.startsWith(APPLICATION_XML)? APPLICATION_XML : APPLICATION_JSON;
        var type = headers.get(TYPE);
        var result = new HashMap<String, Object>();
        var app = new HashMap<String, Object>();
        result.put(APP, app);
        app.put(NAME, platform.getName());
        app.put(VERSION, util.getVersion());
        app.put(DESCRIPTION, appDescription);
        var appId = platform.getAppId();
        if (appId != null) {
            app.put(INSTANCE, appId);
        }
        switch (type) {
            case ROUTES -> {
                if (isServiceMonitor) {
                    result.put(ROUTING, new HashMap<>());
                    result.put(MESSAGE, "Routing table is not visible from a presence monitor");
                } else {
                    var journaledRoutes = po.getJournaledRoutes();
                    if (journaledRoutes.size() > 1) {
                        Collections.sort(journaledRoutes);
                    }
                    result.put(JOURNAL, journaledRoutes);
                    var more = getRoutingTable();
                    if (more != null) {
                        result.put(ROUTING, more);
                    }
                    // add route substitution list if any
                    var substitutions = po.getRouteSubstitutionList();
                    if (!substitutions.isEmpty()) {
                        result.put(ROUTE_SUBSTITUTION, substitutions);
                    }
                }
            }
            case LIB -> result.put(LIBRARY, util.getLibraryList());
            case ENV -> {
                result.put(ENV, getEnv());
                result.put(ROUTING, getRegisteredServices());
            }
            case null, default -> {
                // java VM information
                var jvm = new HashMap<String, Object>();
                result.put(JVM, jvm);
                jvm.put("java_version", System.getProperty(JAVA_VERSION));
                jvm.put("java_vm_version", System.getProperty(JAVA_VM_VERSION));
                jvm.put("java_runtime_version", System.getProperty(JAVA_RUNTIME_VERSION));
                // memory usage
                var runtime = Runtime.getRuntime();
                var number = NumberFormat.getInstance();
                var maxMemory = runtime.maxMemory();
                var allocatedMemory = runtime.totalMemory();
                var freeMemory = runtime.freeMemory();
                var memory = new HashMap<String, Object>();
                result.put(MEMORY, memory);
                memory.put(MAX, number.format(maxMemory));
                memory.put(FREE, number.format(freeMemory));
                memory.put(ALLOCATED, number.format(allocatedMemory));
                memory.put(USED, number.format(allocatedMemory - freeMemory));
                /*
                 * check streams resources if any
                 */
                result.put(STREAMS, ObjectStreamIO.getStreamCount());
                var more = getAdditionalInfo();
                if (more != null) {
                    result.put("additional_info", more);
                }
                result.put(ORIGIN, platform.getOrigin());
                result.put(PERSONALITY, ServerPersonality.getInstance().getType().name());
                var time = new HashMap<String, Object>();
                var now = new Date();
                time.put(START, util.getLocalTimestamp(platform.getStartTime()));
                time.put(CURRENT, util.getLocalTimestamp(now.getTime()));
                result.put(TIME, time);
                result.put(UP_TIME, util.elapsedTime(now.getTime() - platform.getStartTime()));
            }
        }
        return new EventEnvelope().setHeader(CONTENT_TYPE, accept).setBody(result);
    }

    private Object getAdditionalInfo() {
        if (Platform.getInstance().hasRoute(ADDITIONAL_INFO)) {
            var po = EventEmitter.getInstance();
            try {
                var req = new EventEnvelope().setTo(ADDITIONAL_INFO).setHeader(TYPE, QUERY);
                var res = po.request(req, 5000).get();
                return res.getBody();
            } catch (Exception e) {
                return ERROR_FETCHING_INFO + e.getMessage();
            }
        } else {
            return null;
        }
    }

    private Object getEnv() {
        var result = new HashMap<String, Object>();
        var reader = AppConfigReader.getInstance();
        var envVars = util.split(reader.getProperty(SHOW_ENV, ""), ", ");
        var properties = util.split(reader.getProperty(SHOW_PROPERTIES, ""), ", ");
        var missingVars = new ArrayList<String>();
        var eMap = new HashMap<String, Object>();
        if (!envVars.isEmpty()) {
            for (String key:  envVars) {
                var v = System.getenv(key);
                if (v == null) {
                    missingVars.add(key);
                } else {
                    eMap.put(key, v);
                }
            }
        }
        result.put(SYSTEM_ENV, eMap);
        var missingProp = new ArrayList<String>();
        var pMap = new HashMap<String, Object>();
        if (!properties.isEmpty()) {
            for (String key: properties) {
                var v = reader.getProperty(key);
                if (v == null) {
                    missingProp.add(key);
                } else {
                    pMap.put(key, v);
                }
            }
        }
        result.put(APP_PROPS, pMap);
        // any missing keys?
        var missingKeys = new HashMap<String, Object>();
        if (!missingVars.isEmpty()) {
            missingKeys.put(SYSTEM_ENV, missingVars);
        }
        if (!missingProp.isEmpty()) {
            missingKeys.put(APP_PROPS, missingProp);
        }
        if (!missingKeys.isEmpty()) {
            result.put(MISSING, missingKeys);
        }
        return result;
    }

    private Map<String, List<String>> getRegisteredServices() {
        var result = new HashMap<String, List<String>>();
        result.put("public", getLocalRoutingDetails(false));
        result.put("private", getLocalRoutingDetails(true));
        return result;
    }

    private List<String> getLocalRoutingDetails(boolean isPrivate) {
        var result = new ArrayList<String>();
        var map = Platform.getInstance().getLocalRoutingTable();
        for (String route: map.keySet()) {
            var service = map.get(route);
            if (service.isPrivate() == isPrivate) {
                var queue = service.getManager();
                var read = queue.getReadCounter();
                var write = queue.getWriteCounter();
                result.add(route + " (" + queue.getFreeWorkers() + "/" + service.getConcurrency() + ") " +
                                " r/w=" + read + "/" + write);
            }
        }
        if (result.size() > 1) {
            Collections.sort(result);
        }
        return result;
    }

    private Object getRoutingTable() {
        var platform = Platform.getInstance();
        var po = EventEmitter.getInstance();
        if (hasCloudConnector && (platform.hasRoute(ServiceDiscovery.SERVICE_QUERY) ||
                                  platform.hasRoute(CLOUD_CONNECTOR))) {
            try {
                var req = new EventEnvelope().setTo(ServiceDiscovery.SERVICE_QUERY)
                            .setHeader(TYPE, DOWNLOAD).setHeader(ORIGIN, platform.getOrigin());
                var res = po.request(req, 5000).get();
                return res.getBody();
            } catch (Exception e) {
                // ok to ignore
            }
        }
        return getLocalPublicRouting();
    }

    private Map<String, Object> getLocalPublicRouting() {
        var result = new HashMap<String, Object>();
        var map = Platform.getInstance().getLocalRoutingTable();
        for (String route: map.keySet()) {
            var service = map.get(route);
            if (!service.isPrivate()) {
                result.put(route, service.getConcurrency());
            }
        }
        return result;
    }
    
    private Object handleHealth(Map<String, String> headers) throws IOException {
        var platform = Platform.getInstance();
        var po = EventEmitter.getInstance();
        var up = true;
        var result = new HashMap<String, Object>();
        var dependency = new ArrayList<Map<String, Object>>();
        checkServices(dependency, optionalServices, false);
        if (!checkServices(dependency, requiredServices, true)) {
            up = false;
        }
        // save the current health status
        po.send(EventEmitter.ACTUATOR_SERVICES, up, new Kv(TYPE, HEALTH_STATUS));
        // checkServices will update the "dependency" service list
        result.put(DEPENDENCY, dependency);
        if (dependency.isEmpty()) {
            result.put(MESSAGE, "Did you forget to define mandatory.health.dependencies or optional.health.dependencies");
        }
        result.put(STATUS, up? "UP" : "DOWN");
        result.put(ORIGIN, platform.getOrigin());
        result.put(NAME, platform.getName());
        var response = new EventEnvelope().setHeader(CONTENT_TYPE, APPLICATION_JSON)
                                .setBody(result).setStatus(up? 200 : 400);
        var accept = headers.getOrDefault(ACCEPT, "?");
        if (accept.startsWith(APPLICATION_XML)) {
            response.setHeader(CONTENT_TYPE, APPLICATION_XML);
        } else {
            response.setHeader(CONTENT_TYPE, APPLICATION_JSON);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private boolean checkServices(List<Map<String, Object>> dependency, List<String> services, boolean required) {
        var po = EventEmitter.getInstance();
        var up = true;
        for (String route: services) {
            var m = new HashMap<String, Object>();
            m.put(ROUTE, route);
            m.put(REQUIRED, required);
            dependency.add(m);
            try {
                var key = "info/" + route;
                if (!cache.exists(key)) {
                    var req = new EventEnvelope().setTo(route).setHeader(TYPE, INFO);
                    var res = po.request(req, 3000).get();
                    if (res.getBody() instanceof Map) {
                        cache.put(key, res.getBody());
                    }
                }
                var info = cache.get(key);
                if (info instanceof Map) {
                    m.putAll((Map<String, Object>) info);
                }
                var req = new EventEnvelope().setTo(route).setHeader(TYPE, HEALTH);
                var res = po.request(req, 10000).get();
                m.put(STATUS_CODE, res.getStatus());
                if (res.getStatus() != 200) {
                    up = false;
                }
                // only accept text or Map
                if (res.getRawBody() instanceof String || res.getRawBody() instanceof Map) {
                    m.put(MESSAGE, res.getRawBody());
                }
            } catch (Exception e) {
                up = false;
                if (e.getMessage().contains(NOT_FOUND)) {
                    m.put(STATUS_CODE, 404);
                    m.put(MESSAGE, PLEASE_CHECK + e.getMessage());
                } else {
                    m.put(STATUS_CODE, 500);
                    m.put(MESSAGE, e.getMessage());
                }
            }
        }
        return up;
    }
}
