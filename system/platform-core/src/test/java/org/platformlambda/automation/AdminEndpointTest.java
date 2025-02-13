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

package org.platformlambda.automation;

import org.junit.jupiter.api.Test;
import org.platformlambda.common.TestBase;
import org.platformlambda.core.mock.MockCloud;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.system.Platform;
import org.platformlambda.core.util.MultiLevelMap;
import org.platformlambda.core.util.Utility;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AdminEndpointTest extends TestBase {

    @SuppressWarnings("unchecked")
    @Test
    public void infoEndpointTest() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/info", null);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        MultiLevelMap multi = new MultiLevelMap(result);
        assertEquals("platform-core", multi.getElement("app.name"));
        assertEquals("REST", multi.getElement("personality"));
        String origin = Platform.getInstance().getOrigin();
        assertEquals(origin, multi.getElement("origin"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void infoEndpointXmlTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("accept", "application/xml");
        EventEnvelope response = httpGet(localHost, "/info", headers);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        MultiLevelMap multi = new MultiLevelMap(result);
        assertEquals("platform-core", multi.getElement("app.name"));
        assertEquals("REST", multi.getElement("personality"));
        String origin = Platform.getInstance().getOrigin();
        assertEquals(origin, multi.getElement("origin"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void nonExistRemoteInfoEndpointTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", "does-not-exist");
        EventEnvelope response = httpGet(localHost, "/info", headers);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("does-not-exist is not reachable", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void protectInfoEndpointTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        EventEnvelope response = httpGet("http://localhost:"+port, "/info", headers);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("Resource not found", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void libEndpointTest() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/info/lib", null);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        MultiLevelMap multi = new MultiLevelMap(result);
        assertEquals("platform-core", multi.getElement("app.name"));
        assertTrue(result.containsKey("library"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void remoteLibEndpointTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", "does-not-exist");
        EventEnvelope response = httpGet(localHost, "/info/lib", headers);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("does-not-exist is not reachable", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void routeEndpointTest() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/info/routes", null);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertInstanceOf(Map.class, result.get("routing"));
        Map<String, Object> routing = (Map<String, Object>) result.get("routing");
        assertEquals(new HashMap<>(), routing.get("routes"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void remoteRouteEndpointTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", "does-not-exist");
        EventEnvelope response = httpGet(localHost, "/info/routes", headers);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("does-not-exist is not reachable", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void healthEndpointTest() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/health", null);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        MultiLevelMap map = new MultiLevelMap(result);
        assertEquals("UP", map.getElement("status"));
        assertEquals("fine", map.getElement("dependency[0].message"));
        assertEquals(200, map.getElement("dependency[0].status_code"));
        assertEquals("mock.connector", map.getElement("dependency[0].service"));
        // livenessProbe is linked to health check
        response = httpGet(localHost, "/livenessprobe", null);
        assert response != null;
        assertEquals("OK", response.getBody());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void simulateHealthCheckFailureTest() throws IOException, InterruptedException {
        MockCloud.setSimulateException(true);
        EventEnvelope response = httpGet(localHost, "/health", null);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        // failed health check is returned as HTTP-400
        assertEquals(400, response.getStatus());
        MultiLevelMap map = new MultiLevelMap(result);
        assertEquals("DOWN", map.getElement("status"));
        assertEquals("just a test", map.getElement("dependency[0].message"));
        // original status code from dependency service is preserved
        assertEquals(500, map.getElement("dependency[0].status_code"));
        assertEquals("mock.connector", map.getElement("dependency[0].service"));
        // livenessProbe is linked to health check
        response = httpGet(localHost, "/livenessprobe", null);
        assert response != null;
        assertEquals(400, response.getStatus());
        assertEquals("Unhealthy. Please check '/health' endpoint.", response.getBody());
        MockCloud.setSimulateException(false);
        // try it again
        httpGet(localHost, "/health", null);
        response = httpGet(localHost, "/livenessprobe", null);
        assert response != null;
        assertEquals("OK", response.getBody());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void remoteHealthEndpointTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", "does-not-exist");
        EventEnvelope response = httpGet(localHost, "/health", headers);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("does-not-exist is not reachable", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void remoteLivenessEndpointTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", "does-not-exist");
        EventEnvelope response = httpGet(localHost, "/livenessprobe", headers);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("does-not-exist is not reachable", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void envEndpointTest() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/env", null);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        MultiLevelMap multi = new MultiLevelMap(result);
        assertEquals("platform-core", multi.getElement("app.name"));
        assertInstanceOf(Map.class, multi.getElement("env"));
        assertInstanceOf(List.class, multi.getElement("routing.private"));
        assertInstanceOf(List.class, multi.getElement("routing.public"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void remoteEnvEndpointTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", "does-not-exist");
        EventEnvelope response = httpGet(localHost, "/livenessprobe", headers);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("does-not-exist is not reachable", result.get("message"));
    }

    @Test
    public void shutdownUsingGetWillFail() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/shutdown/now", null);
        assert response != null;
        assertEquals(404, response.getStatus());
    }

    @Test
    public void suspendUsingGetWillFail() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/suspend/now", null);
        assert response != null;
        assertEquals(404, response.getStatus());
    }

    @Test
    public void resumeUsingGetWillFail() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/resume/now", null);
        assert response != null;
        assertEquals(404, response.getStatus());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shutdownWithoutAppInstanceWillFail() throws IOException, InterruptedException {
        EventEnvelope response = httpPost(localHost, "/shutdown", null, new HashMap<>());
        assert response != null;
        assertEquals(400, response.getStatus());
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals("Missing X-App-Instance in request header", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shutdownWithIncorrectAppInstanceWillFail() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", "does-not-exist");
        EventEnvelope response = httpPost(localHost, "/shutdown", headers, new HashMap<>());
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("does-not-exist is not reachable", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void suspendWithoutAppInstanceWillFail() throws IOException, InterruptedException {
        EventEnvelope response = httpPost(localHost, "/suspend/now", null, new HashMap<>());
        assert response != null;
        assertEquals(400, response.getStatus());
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals("Missing X-App-Instance in request header", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void suspendWithIncorrectAppInstanceWillFail() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", "does-not-exist");
        EventEnvelope response = httpPost(localHost, "/suspend/now", headers, new HashMap<>());
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("does-not-exist is not reachable", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resumeWithoutAppInstanceWillFail() throws IOException, InterruptedException {
        EventEnvelope response = httpPost(localHost, "/resume/now", null, new HashMap<>());
        assert response != null;
        assertEquals(400, response.getStatus());
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals("Missing X-App-Instance in request header", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resumeWithIncorrectAppInstanceWillFail() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", "does-not-exist");
        EventEnvelope response = httpPost(localHost, "/resume/now", headers, new HashMap<>());
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("does-not-exist is not reachable", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void suspendTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", Platform.getInstance().getOrigin());
        EventEnvelope response = httpPost(localHost, "/suspend/now", headers, new HashMap<>());
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals("suspend", result.get("type"));
        assertEquals(200, result.get("status"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void resumeTest() throws IOException, InterruptedException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-app-instance", Platform.getInstance().getOrigin());
        EventEnvelope response = httpPost(localHost, "/resume/now", headers, new HashMap<>());
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals("resume", result.get("type"));
        assertEquals(200, result.get("status"));
    }

    @Test
    public void getIndexPage() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/index.html", null);
        assert response != null;
        assertEquals("text/html", response.getHeader("content-type"));
        assertNull(response.getHeader("ETag"));
        assertInstanceOf(String.class, response.getBody());
        String text = (String) response.getBody();
        InputStream in = this.getClass().getResourceAsStream("/public/index.html");
        String index = Utility.getInstance().stream2str(in);
        assertEquals(index, text);
    }

    @Test
    public void getCssPage() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/sample.css", null);
        assert response != null;
        assertEquals("text/css", response.getHeader("content-type"));
        assertNotNull(response.getHeader("ETag"));
        assertInstanceOf(String.class, response.getBody());
        String text = (String) response.getBody();
        InputStream in = this.getClass().getResourceAsStream("/public/sample.css");
        String css = Utility.getInstance().stream2str(in);
        assertEquals(css, text);
    }

    @Test
    public void getTextPage() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/sample.txt", null);
        assert response != null;
        assertEquals("text/plain", response.getHeader("content-type"));
        assertInstanceOf(String.class, response.getBody());
        String text = (String) response.getBody();
        InputStream in = this.getClass().getResourceAsStream("/public/sample.txt");
        String plain = Utility.getInstance().stream2str(in);
        assertEquals(plain, text);
    }

    @Test
    public void getJsPage() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/sample.js", null);
        assert response != null;
        assertEquals("text/javascript", response.getHeader("content-type"));
        assertInstanceOf(String.class, response.getBody());
        String text = (String) response.getBody();
        InputStream in = this.getClass().getResourceAsStream("/public/sample.js");
        String js = Utility.getInstance().stream2str(in);
        assertEquals(js, text);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void pageNotExists() throws IOException, InterruptedException {
        EventEnvelope response = httpGet(localHost, "/no_such_page", null);
        assert response != null;
        assertInstanceOf(Map.class, response.getBody());
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        assertEquals(404, response.getStatus());
        assertEquals(404, result.get("status"));
        assertEquals("Resource not found", result.get("message"));
    }

}
