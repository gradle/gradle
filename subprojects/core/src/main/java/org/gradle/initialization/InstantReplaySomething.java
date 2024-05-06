/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.initialization;

import groovy.json.JsonSlurper;
import org.gradle.internal.Cast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class InstantReplaySomething {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstantReplaySomething.class);

    @SuppressWarnings("unchecked")
    public BuildInstantReplay retrieve(URI buildScanUrl, File gradleUserHome) {
        File keyFile = new File(System.getProperty("user.home"), ".gradle/develocity/keys.properties");
        if (keyFile.exists()) {
            Properties properties = new Properties();
            try (FileReader fr = new FileReader(keyFile)) {
                properties.load(fr);
                String token = (String) properties.get(buildScanUrl.getHost());
                String id = buildScanUrl.getPath().split("/")[2];
                Map<String, ?> gradleAttributesJson = buildScanGradleAttributes(buildScanUrl, id, token);
                List<String> requestedTasks = (List<String>) gradleAttributesJson.get("requestedTasks");

                Map<String, ?> testsJson = buildScanTestFailures(buildScanUrl, id, token);
                Map<String, Object> data = (Map<String, Object>)testsJson.get("data");
                List<Map<String, ?>> tests = (List<Map<String, ?>>)data.getOrDefault("tests", Collections.emptyList());
                List<BuildInstantReplay.TestFailure> testFailures = tests.stream().filter(test -> (int)test.get("outcome") == 2).map(test -> {
                    String taskPath = (String)test.get("workUnitName");
                    String testClass = (String)test.get("suiteName");
                    String testMethod = ((String)test.get("name")).split("\\(")[0];
                    return new BuildInstantReplay.TestFailure(taskPath, testClass + "." + testMethod);
                }).collect(Collectors.toList());
                return new BuildInstantReplay(requestedTasks, testFailures);
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private static Map<String, ?> buildScanGradleAttributes(URI buildScanUrl, String id, String token) throws URISyntaxException, IOException {
//        LOGGER.warn("Retrieving build attributes for build scan id {} using token {}", id, token.substring(0, 16));

        URL buildScanData = new URI(buildScanUrl.getScheme(), buildScanUrl.getHost(), "/api/builds/" + id + "/gradle-attributes", null).toURL();
        return getJson(token, buildScanData);
    }

    private static Map<String, ?> buildScanTestFailures(URI buildScanUrl, String id, String token) throws URISyntaxException, IOException {
//        LOGGER.warn("Retrieving failed tests for build scan id {} using token {}", id, token.substring(0, 16));

        // https://ge.gradle.org/scan-data/gradle/xc5mnhehiacoo/tests
        URL buildScanData = new URI(buildScanUrl.getScheme(), buildScanUrl.getHost(), "/scan-data/gradle/" + id + "/tests", null).toURL();
        return getJson(token, buildScanData);
    }

    private static Map<String, ?> getJson(String token, URL buildScanData) throws IOException {
        URLConnection uc = buildScanData.openConnection();
        String basicAuth = "Bearer " + token;
        uc.setRequestProperty ("Authorization", basicAuth);
        try (InputStream in = uc.getInputStream()) {
            Object json = new JsonSlurper().parse(in);
//            LOGGER.warn(json.toString());
            return Cast.uncheckedCast(json);
        }
    }
}
