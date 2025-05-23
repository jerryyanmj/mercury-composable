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

package org.platformlambda.spring.system;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.platformlambda.core.serializers.SimpleMapper;
import org.platformlambda.core.serializers.SimpleXmlWriter;
import org.platformlambda.core.util.Utility;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;

@RestController
public class HttpErrorHandler implements ErrorController {
    private static final Utility util = Utility.getInstance();
    private static final SimpleXmlWriter xmlWriter = new SimpleXmlWriter();
    private static final String ERROR_PATH = "/error";
    private static final String UTF8 = "utf-8";
    private static final String ERROR_MESSAGE = "javax.servlet.error.message";
    private static final String ERROR_EXCEPTION = "javax.servlet.error.exception";
    private static final String STATUS_CODE = "javax.servlet.error.status_code";
    private static final String NOT_FOUND = "Not Found";

    private static final String TEMPLATE = "/errorPage.html";
    private static final String HTTP_UNKNOWN_WARNING = "There may be a problem in processing your request";
    private static final String HTTP_400_WARNING = "The system is unable to process your request";
    private static final String HTTP_500_WARNING = "Something may be broken";
    private static final String TYPE = "type";
    private static final String ERROR = "error";
    private static final String OK = "ok";
    private static final String ACCEPT = "accept";
    private static final String ACCEPT_ANY = "*/*";
    private static final String MESSAGE = "message";
    private static final String STATUS = "status";
    private static final String SET_MESSAGE = "${message}";
    private static final String SET_STATUS = "${status}";
    private static final String SET_WARNING = "${warning}";
    private static String templateFile;

    @GetMapping(path = ERROR_PATH)
    public void handlerError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String message = getError(request);
        Integer status = (Integer) request.getAttribute(STATUS_CODE);
        if (status == null) {
            status = 404;
        }
        if (status == 404 && message.isEmpty()) {
            message = NOT_FOUND;
        }
        HttpErrorHandler.sendResponse(response, status, message, request.getHeader(ACCEPT));
    }

    private String getError(HttpServletRequest request) {
        String message = (String) request.getAttribute(ERROR_MESSAGE);
        if (message != null && !message.isEmpty()) {
            return message;
        }
        Object exception = request.getAttribute(ERROR_EXCEPTION);
        if (exception instanceof Throwable ex) {
            return ex.getMessage();
        }
        return "";
    }

    public static void sendResponse(HttpServletResponse response, int status, String message, String accept)
            throws IOException {
        if (templateFile == null) {
            templateFile = util.stream2str(HttpErrorHandler.class.getResourceAsStream(TEMPLATE));
        }
        HashMap<String, Object> error = new HashMap<>();
        error.put(TYPE, status < 300? OK : ERROR);
        error.put(MESSAGE, message);
        error.put(STATUS, status);
        final String contentType;
        if (accept == null) {
            contentType = MediaType.APPLICATION_JSON_VALUE;
        } else if (accept.contains(MediaType.TEXT_HTML_VALUE)) {
            contentType = MediaType.TEXT_HTML_VALUE;
        } else if (accept.contains(MediaType.APPLICATION_XML_VALUE)) {
            contentType = MediaType.APPLICATION_XML_VALUE;
        } else if (accept.contains(MediaType.APPLICATION_JSON_VALUE) || accept.contains(ACCEPT_ANY)) {
            contentType = MediaType.APPLICATION_JSON_VALUE;
        } else {
            contentType = MediaType.TEXT_PLAIN_VALUE;
        }
        response.setStatus(status);
        response.setCharacterEncoding(UTF8);
        response.setContentType(contentType);
        if (contentType.equals(MediaType.TEXT_HTML_VALUE)) {
            String errorPage = templateFile.replace(SET_STATUS, String.valueOf(status)).replace(SET_MESSAGE, message);
            if (status >= 500) {
                errorPage = errorPage.replace(SET_WARNING, HTTP_500_WARNING);
            } else if (status >= 400) {
                errorPage = errorPage.replace(SET_WARNING, HTTP_400_WARNING);
            } else {
                errorPage = errorPage.replace(SET_WARNING, HTTP_UNKNOWN_WARNING);
            }
            response.getOutputStream().write(util.getUTF(errorPage));
        } else if (contentType.equals(MediaType.APPLICATION_JSON_VALUE) ||
                    contentType.equals(MediaType.TEXT_PLAIN_VALUE)) {
            response.getOutputStream().write(util.getUTF(SimpleMapper.getInstance().getMapper()
                    .writeValueAsString(error)));
        } else {
            response.getOutputStream().write(util.getUTF(xmlWriter.write(ERROR, error)));
        }
    }
}
