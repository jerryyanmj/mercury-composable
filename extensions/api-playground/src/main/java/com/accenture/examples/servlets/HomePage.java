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

package com.accenture.examples.servlets;

import org.platformlambda.core.util.AppConfigReader;
import org.platformlambda.core.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@WebServlet("/index.html")
public class HomePage extends HttpServlet {
	private static final Logger log = LoggerFactory.getLogger(HomePage.class);

	@Serial
	private static final long serialVersionUID = -3607030982796747671L;

	private static final String YAML = ".yaml";
	private static final String JSON = ".json";
	private static final String APP = "app";
	private static final String HYPERLINK = "<a class=\"dropdown-item\" href=";
	private static final String END_HYPERLINK = "</a>";
	private static String indexPage;
	private static File dir;
	private static Boolean ready = false;

	public HomePage() {
		if (indexPage == null || dir == null) {
			try (InputStream swagger = this.getClass().getResourceAsStream("/swagger-ui/index.html")) {
				if (swagger != null) {
					ready = true;
					InputStream res = this.getClass().getResourceAsStream("/index.html");
					indexPage = Utility.getInstance().stream2str(res);
					AppConfigReader config = AppConfigReader.getInstance();
					String location = config.getProperty("api.playground.apps", "/tmp/api-playground");
					dir = new File(location);
				} else {
					throw new IOException("Missing '/resources/swagger-ui/index.html'");
				}
			} catch (IOException e) {
				log.error("Unable to load home page - {}", e.getMessage());
			}
		}
	}

	@Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (!ready) {
			response.sendError(404, "Did you forget to do " +
					"'git clone https://github.com/swagger-api/swagger-ui.git' " +
					"and copy the dist folder to the swagger-ui resource folder?");
			return;
		}
		disableBrowserCache(response);
		Utility util = Utility.getInstance();

		String result = indexPage;
		StringBuilder sb = new StringBuilder();
		List<String> files = getFiles();
		if (files.isEmpty()) {
			sb.append(HYPERLINK);
			sb.append('\"');
			sb.append("#");
			sb.append('\"');
			sb.append('>');
			sb.append("No applications");
			sb.append(END_HYPERLINK);
		} else {
			for (String name: files) {
				sb.append(HYPERLINK);
				sb.append('\"');
				sb.append("/?app=");
				sb.append(name);
				sb.append('\"');
				sb.append('>');
				sb.append(name);
				sb.append(END_HYPERLINK);
			}
		}
		result = result.replace("$dropdown", sb.toString());
		String app = safeText(request.getParameter(APP));
		if (app == null) {
			result = result.replace("$url", "/playground/home.yaml");
		} else {
			File target = new File(dir, app);
			if (target.exists()) {
				result = result.replace("$url", "/api/specs/" + app);
			} else {
				response.sendError(404, "Application " + app + " not found");
				return;
			}
		}
        response.getOutputStream().write(util.getUTF(result));
    }

	private String safeText(String text) {
		if (text == null) {
			return null;
		} else {
			return text.replace("<", "")
						.replace(">", "")
						.replace("&", "");
		}
	}

    private List<String> getFiles() {
		List<String> result = new ArrayList<>();
		if (dir.exists() && dir.isDirectory()) {
			File[] files = dir.listFiles();
			if (files != null) {
				for (File f : files) {
					if (f.getName().endsWith(YAML) || f.getName().endsWith(JSON)) {
						result.add(f.getName());
					}
				}
			}
		}
		if (result.size() > 1) {
			Collections.sort(result);
		}
		return result;
	}

	private void disableBrowserCache(HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-cache, no-store");
		response.setHeader("Pragma", "no-cache");
		response.setDateHeader("Expires", 0);
	}

}
