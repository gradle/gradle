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
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
        /*
        GET https://ge.gradle.org/api/builds/{{id}}/gradle-attributes
        Authorization: Bearer {{api_key}}
         */
                String id = buildScanUrl.getPath().split("/")[2];
                LOGGER.warn("Retrieving build attributes for build scan id {} using token {}", id, token.substring(0, 16));

                URL buildScanData = new URI(buildScanUrl.getScheme(), buildScanUrl.getHost(), "/api/builds/" + id + "/gradle-attributes", null).toURL();
                URLConnection uc = buildScanData.openConnection();
                String basicAuth = "Bearer " + token;
                uc.setRequestProperty ("Authorization", basicAuth);
                try (InputStream in = uc.getInputStream()) {
                    Object json = new JsonSlurper().parse(in);
                    List<String> requestedTasks = (List<String>) ((Map<?, ?>) json).get("requestedTasks");
                    LOGGER.warn(json.toString());

                    return new BuildInstantReplay(requestedTasks);
                }
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return new BuildInstantReplay(Arrays.asList("help"));
    }
}
