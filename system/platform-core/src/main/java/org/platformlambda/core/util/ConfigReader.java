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

import org.platformlambda.core.serializers.SimpleMapper;
import org.platformlambda.core.util.common.ConfigBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConfigReader implements ConfigBase {
    private static final Logger log = LoggerFactory.getLogger(ConfigReader.class);

    private static final ConcurrentMap<String, List<String>> loopDetection = new ConcurrentHashMap<>();
    private static final String CLASSPATH = "classpath:";
    private static final String FILEPATH = "file:";
    private static final String JSON = ".json";
    private static final String YML = ".yml";
    private static final String YAML = ".yaml";
    private static final String DOT_PROPERTIES = ".properties";

    private static AppConfigReader baseConfig;
    private MultiLevelMap config = new MultiLevelMap();
    private final Map<String, Object> cachedFlatMap = new HashMap<>();

    /**
     * Set the base configuration reader (AppConfigReader)
     * Note that this is done automatically when your application starts.
     * You only need to set this when you are running unit tests for the
     * config reader without starting the platform module.
     *
     * @param config is the singleton AppConfigReader class
     */
    public static void setBaseConfig(AppConfigReader config) {
        if (ConfigReader.baseConfig == null) {
            ConfigReader.baseConfig = config;
        }
    }

    /**
     * Retrieve a parameter value by key
     * (Note that a parameter may be substituted by a system property,
     * an environment variable or another configuration parameter key-value
     * using the standard dot-bracket syntax)
     *
     * @param key of a configuration parameter
     * @return parameter value
     */
    @Override
    public Object get(String key) {
        return get(key, null);
    }

    private String getSystemProperty(String key) {
        if (key.isEmpty()) {
            return null;
        }
        return System.getProperty(key);
    }

    /**
     * Retrieve a parameter value by key, given a default value
     * <p>
     * 1. a parameter may be substituted by a system property,
     * an environment variable or another configuration parameter key-value
     * using the standard dot-bracket syntax
     * <p>
     * 2. the optional "loop" parameter should have zero or one element.
     * <p>
     * @param key of a configuration parameter
     * @param defaultValue if key does not exist
     * @param loop reserved for internal use to detect configuration loops
     * @return parameter value
     */
    @Override
    public Object get(String key, Object defaultValue, String... loop) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String systemProperty = getSystemProperty(key);
        if (systemProperty != null) {
            return systemProperty;
        }
        Object value = config.getElement(key);
        if (value == null) {
            value = defaultValue;
        }
        if (value instanceof String result) {
            List<String> intermediateResult = resolveEnvVar(key, result, defaultValue, loop);
            // scan for additional environment variables or system property
            return resolveEnvVar(intermediateResult, key, defaultValue, loop);
        }
        return value;
    }

    private String resolveEnvVar(List<String> intermediateResult,
                                       String key, Object defaultValue, String... loop) {
        if (intermediateResult.size() == 2) {
            String text = intermediateResult.get(1);
            while (hasEnvVar(text)) {
                List<String> nextStage = resolveEnvVar(key, text, defaultValue, loop);
                if (nextStage.size() == 2) {
                    text = nextStage.get(1);
                } else {
                    break;
                }
            }
            return text;
        }
        return intermediateResult.getFirst();
    }

    private boolean hasEnvVar(String text) {
        int bracketStart = text.indexOf("${");
        int bracketEnd = bracketStart != -1? text.indexOf('}', bracketStart) : -1;
        return bracketStart != -1 && bracketEnd != -1;
    }

    private List<String> resolveEnvVar(String key, String original, Object defaultValue, String... loop) {
        List<String> result = new ArrayList<>();
        result.add(original);
        int bracketStart = original.indexOf("${");
        int bracketEnd = bracketStart != -1? original.indexOf('}', bracketStart) : -1;
        if (bracketStart != -1 && bracketEnd != -1 && baseConfig != null) {
            String middle = original.substring(bracketStart + 2, bracketEnd).trim();
            String middleDefault = null;
            if (!middle.isEmpty()) {
                String loopId = loop.length == 1 && !loop[0].isEmpty() ? loop[0] : Utility.getInstance().getUuid();
                int colon = middle.indexOf(':');
                if (colon > 0) {
                    middleDefault = middle.substring(colon+1);
                    middle = middle.substring(0, colon);
                }
                String property = System.getenv(middle);
                if (property != null) {
                    middle = property;
                } else {
                    List<String> refs = loopDetection.getOrDefault(loopId, new ArrayList<>());
                    if (refs.contains(middle)) {
                        log.warn("Config loop for '{}' detected", key);
                        middle = "";
                    } else {
                        refs.add(middle);
                        loopDetection.put(loopId, refs);
                        Object mid = baseConfig.get(middle, defaultValue, loopId);
                        middle = mid != null? String.valueOf(mid) : null;
                    }
                }
                loopDetection.remove(loopId);
                String first = original.substring(0, bracketStart);
                String last = original.substring(bracketEnd+1);
                if (first.isEmpty() && last.isEmpty()) {
                    result.add(middle != null? middle : middleDefault);
                } else {
                    if (middleDefault == null) {
                        middleDefault = "";
                    }
                    result.add(first + (middle != null ? middle : middleDefault) + last);
                }
            }
        }
        return result;
    }

    /**
     * Retrieve a parameter value by key with return value enforced as a string
     *
     * @param key of a configuration parameter
     * @return parameter value as a string
     */
    @Override
    public String getProperty(String key) {
        Object o = get(key);
        return o != null? String.valueOf(o) : null;
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
        String s = getProperty(key);
        return s != null? s : defaultValue;
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
        if (cachedFlatMap.isEmpty()) {
            Map<String, Object> map = Utility.getInstance().getFlatMap(config.getMap());
            for (String key : map.keySet()) {
                cachedFlatMap.put(key, this.get(key));
            }
        }
        return cachedFlatMap;
    }

    /**
     * Check if a key exists
     *
     * @param key of a configuration parameter
     * @return true if key exists
     */
    @Override
    public boolean exists(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
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
     * Load a configuration file into a config reader
     *
     * @param path of the configuration file prefix with "classpath:/" or "file:/"
     * @throws IOException if file not found
     */
    @SuppressWarnings("unchecked")
    public void load(String path) throws IOException {
        boolean isYaml = path.endsWith(YML) || path.endsWith(YAML);
        // ".yaml" and ".yml" can be used interchangeably
        String alternativePath = null;
        if (isYaml) {
            String pathWithoutExt = path.substring(0, path.lastIndexOf('.'));
            alternativePath = path.endsWith(YML)? pathWithoutExt + YAML : pathWithoutExt + YML;
        }
        InputStream in = null;
        if (path.startsWith(FILEPATH)) {
            String filePath = path.substring(FILEPATH.length());
            File f = new File(filePath);
            try {
                if (f.exists()) {
                    in = Files.newInputStream(Paths.get(filePath));
                } else {
                    if (alternativePath != null) {
                        in = Files.newInputStream(Paths.get(alternativePath.substring(FILEPATH.length())));
                    }
                }
            } catch (IOException e) {
                // ok to ignore
            }
        } else {
            String resourcePath = path.startsWith(CLASSPATH)? path.substring(CLASSPATH.length()) : path;
            if (alternativePath != null && alternativePath.startsWith(CLASSPATH)) {
                alternativePath = alternativePath.substring(CLASSPATH.length());
            }
            in = ConfigReader.class.getResourceAsStream(resourcePath);
            if (in == null && alternativePath != null) {
                in = ConfigReader.class.getResourceAsStream(alternativePath);
            }
        }
        if (in == null) {
            throw new IOException(path + " not found");
        }
        try {
            if (isYaml) {
                Utility util = Utility.getInstance();
                Yaml yaml = new Yaml();
                String data = util.getUTF(util.stream2bytes(in, false));
                Map<String, Object> m = yaml.load(data.contains("\t")? data.replace("\t", "  ") : data);
                config = getMultiLevelMap(m);
            } else if (path.endsWith(JSON)) {
                Map<String, Object> m = SimpleMapper.getInstance().getMapper().readValue(in, Map.class);
                config = getMultiLevelMap(m);
            } else if (path.endsWith(DOT_PROPERTIES)) {
                config = new MultiLevelMap();
                Properties p = new Properties();
                p.load(in);
                Map<String, Object> map = new HashMap<>();
                p.forEach((k,v) -> map.put(String.valueOf(k), v));
                List<String> keys = new ArrayList<>(map.keySet());
                Collections.sort(keys);
                keys.forEach(k -> config.setElement(k, map.get(k)));
            }
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                //
            }
        }
    }

    /**
     * Normalized a raw map where the input map may be null
     *
     * @param raw input map
     * @return multi level map
     */
    private MultiLevelMap getMultiLevelMap(Map<String, Object> raw) {
        if (raw == null) {
            return new MultiLevelMap();
        } else {
            enforceKeysAsText(raw);
            return new MultiLevelMap(normalizeMap(raw));
        }
    }

    /**
     * Load a configuration file into a config reader
     *
     * @param map of key-values
     */
    public void load(Map<String, Object> map) {
        enforceKeysAsText(map);
        config = new MultiLevelMap(normalizeMap(map));
    }

    private Map<String, Object> normalizeMap(Map<String, Object> map) {
        Map<String, Object> flat = Utility.getInstance().getFlatMap(map);
        List<String> keys = new ArrayList<>(flat.keySet());
        Collections.sort(keys);
        MultiLevelMap multiMap = new MultiLevelMap();
        keys.forEach(k -> multiMap.setElement(k, flat.get(k)));
        return multiMap.getMap();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void enforceKeysAsText(Map raw) {
        Set keys = new HashSet(raw.keySet());
        for (Object k: keys) {
            Object v = raw.get(k);
            // key is assumed to be string
            if (!(k instanceof String)) {
                raw.remove(k);
                raw.put(String.valueOf(k), v);
            }
            if (v instanceof Map map) {
                enforceKeysAsText(map);
            }
        }
    }
}
