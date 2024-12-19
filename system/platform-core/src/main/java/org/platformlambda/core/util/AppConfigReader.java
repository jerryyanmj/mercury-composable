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

package org.platformlambda.core.util;

import org.platformlambda.core.util.common.ConfigBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class AppConfigReader implements ConfigBase {
    private static final Logger log = LoggerFactory.getLogger(AppConfigReader.class);
    private static final String APP_CONFIG_READER_YML = "app-config-reader.yml";
    private static final String RESOURCES = "resources";
    private static final String SPRING_ACTIVE_PROFILES = "spring.profiles.active";
    private static final String APPLICATION_PREFIX = "application-";
    private static final ConfigReader config = new ConfigReader();
    private static final AppConfigReader INSTANCE = new AppConfigReader();

    public static AppConfigReader getInstance() {
        return INSTANCE;
    }

    /**
     * This is the singleton object to hold the base configuration files
     * application.yml and application.properties.
     * <p>
     * Note that you can provide one or both files in the "resources" folder.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private AppConfigReader() {
        ConfigReader.setBaseConfig(this);
        try (InputStream in = AppConfigReader.class.getResourceAsStream("/"+APP_CONFIG_READER_YML)) {
            if (in == null) {
                throw new IOException("missing "+APP_CONFIG_READER_YML);
            }
            Utility util = Utility.getInstance();
            Yaml yaml = new Yaml();
            String data = util.getUTF(util.stream2bytes(in, false));
            Map<String, Object> m = yaml.load(data.contains("\t")? data.replace("\t", "  ") : data);
            Object fileList = m.get(RESOURCES);
            if (fileList instanceof List list) {
                final Map<String, Object> consolidated = new HashMap<>();
                list.forEach(filename -> mergeConfig(consolidated, filename.toString()));
                // check for the parameter spring.profiles.active
                List<String> profiles = getActiveProfiles(consolidated);
                int n1 = 0;
                for (String profile: profiles) {
                    String additionalProp = APPLICATION_PREFIX+profile+".properties";
                    String additionalYaml = APPLICATION_PREFIX+profile+".yml";
                    n1 += mergeConfig(consolidated, additionalProp);
                    n1 += mergeConfig(consolidated, additionalYaml);
                }
                if (n1 > 0) {
                    log.info("Updated {} parameter{} from active profiles {}", n1, n1 == 1? "" : "s", profiles);
                } else if (!profiles.isEmpty()) {
                    log.info("Active profiles {} contain no additional parameters", profiles);
                }
                MultiLevelMap multiMap = new MultiLevelMap();
                List<String> keys = new ArrayList<>(consolidated.keySet());
                Collections.sort(keys);
                keys.forEach(k -> multiMap.setElement(k, consolidated.get(k)));
                config.load(multiMap.getMap());
                // resolve references to system properties and environment variables from the newly created config
                int n2 = 0;
                for (String k: keys) {
                    Object o = consolidated.get(k);
                    if (o instanceof String v) {
                        int start = v.indexOf("${");
                        int end = v.indexOf('}');
                        if (start != -1 && end != -1 && end > start) {
                            n2++;
                        }
                    }
                }
                // if found, reload configuration
                if (n2 > 0) {
                    keys.forEach(k -> multiMap.setElement(k, config.get(k)));
                    config.load(multiMap.getMap());
                    log.info("Resolved {} key-value{} from system properties and environment variables",
                            n2, n2 == 1? "" : "s");
                }
            } else {
                throw new IOException("missing 'resources' section in "+APP_CONFIG_READER_YML);
            }

        } catch (IOException e) {
            log.error("Unable to parse base application configuration - {}", e.getMessage());
        }
        if (config.isEmpty()) {
            log.error("Application config is empty. Please check.");
        }
    }

    private List<String> getActiveProfiles(Map<String, Object> consolidated) {
        String activeProfiles = System.getProperty(SPRING_ACTIVE_PROFILES);
        if (activeProfiles == null) {
            Object ap = consolidated.get(SPRING_ACTIVE_PROFILES);
            if (ap instanceof String profiles) {
                activeProfiles = profiles;
            }
        }
        return activeProfiles == null? Collections.emptyList() : Utility.getInstance().split(activeProfiles, ", ");
    }

    private int mergeConfig(Map<String, Object> consolidated, String filename) {
        ConfigReader reader = new ConfigReader();
        try {
            reader.load("/"+filename);
            Map<String, Object> flat = Utility.getInstance().getFlatMap(reader.getMap());
            if (!flat.isEmpty()) {
                consolidated.putAll(flat);
                log.info("Loaded {}", filename);
            }
            return flat.size();
        } catch (IOException e) {
            // ok to ignore
            return 0;
        }
    }

    /**
     * Retrieve a parameter value by key
     *
     * @param key of a configuration parameter
     * @return parameter value
     */
    @Override
    public Object get(String key) {
        return config.get(key);
    }

    /**
     * Retrieve a parameter value by key, given a default value
     *
     * @param key of a configuration parameter
     * @param defaultValue if key does not exist
     * @param loop reserved for internal use to detect configuration loops
     * @return parameter value
     */
    @Override
    public Object get(String key, Object defaultValue, String... loop) {
        return config.get(key, defaultValue, loop);
    }

    /**
     * Retrieve a parameter value by key with return value enforced as a string
     *
     * @param key of a configuration parameter
     * @return parameter value as a string
     */
    @Override
    public String getProperty(String key) {
        Object value = get(key);
        if (value instanceof String str) {
            return str;
        } else {
            return value == null? null : String.valueOf(value);
        }
    }

    /**
     * Retrieve a parameter value by key with return value enforced as a string, given a default value
     *
     * @param key of a configuration parameter
     * @param defaultValue if key does not exist
     * @return parameter value as a string
     */
    @Override
    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value == null? defaultValue : value;
    }

    /**
     * Check if a key exists
     *
     * @param key of a configuration parameter
     * @return true if key exists
     */
    @Override
    public boolean exists(String key) {
        return config.exists(key);
    }

    /**
     * Check if the configuration file is empty
     *
     * @return true if empty
     */
    @Override
    public boolean isEmpty() {
        return config.isEmpty();
    }

    /**
     * Retrieve the underlying map
     * (Note that this returns a raw map without value substitution)
     *
     * @return map of key-values
     */
    @Override
    public Map<String, Object> getMap() {
        return config.getMap();
    }

    /**
     * Retrieve a flat map of composite key-values
     * (Value substitution is automatically applied)
     *
     * @return flat map
     */
    @Override
    public Map<String, Object> getCompositeKeyValues() {
        return config.getCompositeKeyValues();
    }

}