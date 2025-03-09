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

package com.accenture.models;

import com.accenture.automation.TaskExecutor;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.system.EventEmitter;
import org.platformlambda.core.util.Utility;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FlowInstance {
    private static final String MODEL = "model";
    private static final String FLOW = "flow";
    private static final String TIMEOUT = "timeout";
    private static final String INSTANCE = "instance";
    private static final String CID = "cid";
    private static final String TRACE = "trace";
    private static final String PARENT = "parent";

    // dataset is the state machine that holds the original input and the latest model
    public final ConcurrentMap<String, Object> dataset = new ConcurrentHashMap<>();
    public final AtomicInteger pipeCounter = new AtomicInteger(0);
    public final ConcurrentMap<Integer, PipeInfo> pipeMap = new ConcurrentHashMap<>();
    public final ConcurrentLinkedQueue<String> tasks = new ConcurrentLinkedQueue<>();
    public final ConcurrentMap<String, Boolean> pendingTasks = new ConcurrentHashMap<>();
    private final long start = System.currentTimeMillis();
    public final String id = Utility.getInstance().getUuid();
    public final String cid;
    public final String replyTo;
    private final String timeoutWatcher;
    private final Flow flow;
    private String traceId;
    private String tracePath;
    private boolean responded = false;
    private boolean running = true;

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     *
     * @param flowId of the event flow configuration
     * @param cid correlation ID
     * @param replyTo of the caller to a flow adapter
     * @param flow event flow configuration
     * @param parentId is the parent flow instance ID
     */
    public FlowInstance(String flowId, String cid, String replyTo, Flow flow, String parentId) {
        this.flow = flow;
        this.cid = cid;
        this.replyTo = replyTo;
        // initialize the state machine
        ConcurrentMap<String, Object> model = new ConcurrentHashMap<>();
        model.put(INSTANCE, id);
        model.put(CID, cid);
        model.put(FLOW, flowId);
        // this is a sub-flow if parent flow instance is available
        var parent = Flows.getFlowInstance(parentId);
        if (parent != null) {
            model.put(PARENT, parent.dataset.get(MODEL));
        }
        this.dataset.put(MODEL, model);
        EventEmitter po = EventEmitter.getInstance();
        EventEnvelope timeoutTask = new EventEnvelope();
        timeoutTask.setTo(TaskExecutor.SERVICE_NAME).setCorrelationId(id).setHeader(TIMEOUT, true);
        this.timeoutWatcher = po.sendLater(timeoutTask, new Date(System.currentTimeMillis() + flow.ttl));
    }

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     *
     * @param traceId for tracing
     * @param tracePath for tracing
     */
    @SuppressWarnings("unchecked")
    public void setTrace(String traceId, String tracePath) {
        this.setTraceId(traceId);
        this.setTracePath(tracePath);
        if (traceId != null) {
            ConcurrentMap<String, Object> model = (ConcurrentMap<String, Object>) dataset.get(MODEL);
            model.put(TRACE, traceId);
        }
    }

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     *
     * @return start time of a flow instance
     */
    public long getStartMillis() {
        return start;
    }

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     */
    public void close() {
        if (running) {
            running = false;
            EventEmitter.getInstance().cancelFutureEvent(timeoutWatcher);
        }
    }

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     *
     * @return true if event flow is outstanding
     */
    public boolean isNotResponded() {
        return !responded;
    }

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     *
     * @param responded if a response has been sent to the caller
     */
    public void setResponded(boolean responded) {
        this.responded = responded;
    }

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     *
     * @return trace ID
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     *
     * @param traceId for tracing
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     *
     * @return trace path
     */
    public String getTracePath() {
        return tracePath;
    }

    /**
     * This is reserved for system use.
     * DO NOT use this directly in your application code.
     *
     * @param tracePath for tracing
     */
    public void setTracePath(String tracePath) {
        this.tracePath = tracePath;
    }

    /**
     * Retrieve the event flow configuration
     *
     * @return event flow configuration
     */
    public Flow getFlow() {
        return flow;
    }
}
