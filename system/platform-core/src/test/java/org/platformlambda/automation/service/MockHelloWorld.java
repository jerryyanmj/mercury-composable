/*

    Copyright 2018-2024 Accenture Technology

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

package org.platformlambda.automation.service;

import org.platformlambda.core.annotations.KernelThreadRunner;
import org.platformlambda.core.models.AsyncHttpRequest;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.models.TypedLambdaFunction;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KernelThreadRunner is added for unit test purpose.
 * <p>
 * Normally KernelThread should be reserved for computational intensive functions
 * without RPC calls
 */
@KernelThreadRunner
public class MockHelloWorld implements TypedLambdaFunction<AsyncHttpRequest, Object> {

    @Override
    public Object handleEvent(Map<String, String> headers, AsyncHttpRequest body, int instance) throws IOException {
        AsyncHttpRequest input = new AsyncHttpRequest(body); // test AsyncHttpRequest clone feature
        if ("HEAD".equals(input.getMethod())) {
            EventEnvelope result = new EventEnvelope().setHeader("X-Response", "HEAD request received")
                                        .setHeader("Content-Length", 100);
            result.setHeader("Set-Cookie", "first=cookie|second=one");
            return result;
        }
        if (input.getStreamRoute() != null) {
            return new EventEnvelope().setBody(input.getBody()).setHeader("stream", input.getStreamRoute())
                    .setHeader("content-type", "application/octet-stream");
        } else if (input.getBody() instanceof byte[]) {
            return new EventEnvelope().setBody(input.getBody())
                    .setHeader("content-type", "application/octet-stream");
        } else {
            // returning AsyncHttpRequest is an edge case
            return body;
        }
    }
}
