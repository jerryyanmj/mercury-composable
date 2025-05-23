/*

    Copyright 2018-2025 Accenture Technology

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

 */

package org.platformlambda.cloud;

import org.platformlambda.core.models.LambdaFunction;
import org.platformlambda.core.system.Platform;
import org.platformlambda.core.system.PubSub;
import org.platformlambda.core.util.SimpleCache;
import org.platformlambda.core.util.Utility;
import org.platformlambda.core.websocket.common.MultipartPayload;
import org.platformlambda.cloud.services.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This is reserved for system use.
 * DO NOT use this directly in your application code.
 */
public class EventProducer implements LambdaFunction {
    private static final Logger log = LoggerFactory.getLogger(EventProducer.class);
    /*
     * DO NOT REMOVE THE DATA TYPES BELOW
     * -----------------------------------
     * Embedded data types are used for encoding by the concrete implementation of cloud connector
     * EMBED_EVENT, RECIPIENT, DATA_TYPE, TEXT_DATA, BYTES_DATA, MAP_DATA, LIST_DATA
     */
    public static final String EMBED_EVENT = "_event_";
    public static final String RECIPIENT = "_rx_";
    public static final String DATA_TYPE = "_data_";
    public static final String TEXT_DATA = "text";
    public static final String BYTES_DATA = "bytes";
    public static final String MAP_DATA = "map";
    public static final String LIST_DATA = "list";
    private static final long ONE_MINUTE = 60 * 1000;
    private static final long TEN_MINUTES = 10 * ONE_MINUTE;
    private static final SimpleCache stickyDest = SimpleCache.createCache("sticky.destinations", ONE_MINUTE);
    private static final SimpleCache workLoad = SimpleCache.createCache("service.load.balancer", TEN_MINUTES);
    private static final String ID = MultipartPayload.ID;
    private static final String COUNT = MultipartPayload.COUNT;
    private static final String TOTAL = MultipartPayload.TOTAL;
    private static final String TO = MultipartPayload.TO;
    private static final String BROADCAST = MultipartPayload.BROADCAST;
 
    @Override
    public Object handleEvent(Map<String, String> headers, Object input, int instance) throws Exception {
        if (headers.containsKey(TO) && input instanceof byte[] payload) {
            List<String> destinations = getDestinations(headers);
            if (!destinations.isEmpty()) {
                PubSub ps = PubSub.getInstance();
                Utility util = Utility.getInstance();
                for (String target : destinations) {
                    String topicPartition = ServiceRegistry.getTopic(target);
                    if (topicPartition != null) {
                        final String topic;
                        int partition = -1;
                        if (topicPartition.contains("-")) {
                            int separator = topicPartition.lastIndexOf('-');
                            topic = topicPartition.substring(0, separator);
                            partition = util.str2int(topicPartition.substring(separator + 1));
                        } else {
                            topic = topicPartition;
                        }
                        Map<String, String> parameters = new HashMap<>();
                        parameters.put(EMBED_EVENT, "1");
                        parameters.put(RECIPIENT, target);
                        ps.publish(topic, partition, parameters, payload);
                    }
                }
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<String> getDestinations(Map<String, String> headers) {
        String to = headers.get(TO);
        boolean broadcast = headers.containsKey(BROADCAST);
        String id = headers.get(ID);
        String count = headers.get(COUNT);
        String total = headers.get(TOTAL);
        boolean isSegmented = id != null && count != null && total != null;
        if (isSegmented) {
            Object cached = stickyDest.get(id);
            if (cached instanceof List) {
                // clear cache because this is the last block
                if (count.equals(total)) {
                    stickyDest.remove(id);
                } else {
                    // reset expiry timer
                    stickyDest.put(id, cached);
                }
                log.debug("cached target {} for {} {} {}", cached, id, count, total);
                return (List<String>) cached;
            }
        }
        // normal message
        Platform platform = Platform.getInstance();
        if (to.contains("@")) {
            String target = to.substring(to.indexOf('@') + 1);
            if (ServiceRegistry.destinationExists(target)) {
                return Collections.singletonList(target);
            }
        } else {
            if (!broadcast && platform.hasRoute(to)) {
                // use local routing
                return Collections.singletonList(platform.getOrigin());
            }
            Map<String, String> targets = ServiceRegistry.getDestinations(to);
            if (targets != null) {
                List<String> available = new ArrayList<>(targets.keySet());
                if (!available.isEmpty()) {
                    if (broadcast) {
                        if (isSegmented) {
                            stickyDest.put(id, available);
                        }
                        return available;
                    } else {
                        String target = getNextAvailable(available);
                        if (target != null) {
                            List<String> result = Collections.singletonList(target);
                            if (isSegmented) {
                                stickyDest.put(id, result);
                            }
                            return result;
                        }
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private String getNextAvailable(List<String> targetList) {
        List<String> available = new ArrayList<>();
        for (String target: targetList) {
            if (ServiceRegistry.destinationExists(target)) {
                available.add(target);
            }
        }
        if (available.isEmpty()) {
            return null;
        } else if (available.size() == 1) {
            return available.getFirst();
        } else {
            // select target using round-robin protocol
            Map<String, Long> load = new HashMap<>();
            for (String target: available) {
                Object o = workLoad.get(target);
                if (o instanceof Long n) {
                    load.put(target, n);
                }
            }
            // if new member(s) discovered, reset counts
            if (load.size() < available.size()) {
                for (String target: available) {
                    load.put(target, 0L);
                }
            }
            String selected = available.getFirst();
            long lowest = load.get(selected);
            // find the lowest load
            for (Map.Entry<String, Long> target: load.entrySet()) {
                if (target.getValue() < lowest) {
                    lowest = target.getValue();
                    selected = target.getKey();
                }
            }
            for (String target: available) {
                long v = load.get(target);
                // increment count for the selected target
                workLoad.put(target, target.equals(selected)? v + 1 : v);
            }
            return selected;
        }
    }

}
