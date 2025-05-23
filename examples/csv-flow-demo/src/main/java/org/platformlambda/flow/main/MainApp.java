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

package org.platformlambda.flow.main;

import org.platformlambda.core.annotations.MainApplication;
import org.platformlambda.core.models.EntryPoint;
import org.platformlambda.core.system.AutoStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@MainApplication
public class MainApp implements EntryPoint {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    /**
     * This main class is only used when testing the app from the IDE.
     *
     * @param args - command line arguments
     */
    public static void main(String[] args) {
        AutoStart.main(args);
    }

    @Override
    public void start(String[] args) {
        // ensure demo staging area is ready
        ensureDir(new File("/tmp/staging"));
        ensureDir(new File("/tmp/archive"));
        log.info("Started");
    }

    private void ensureDir(File dir) {
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                log.info("Folder {} created", dir);
            }
        }
    }
}
