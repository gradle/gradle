/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.wrapper;

import org.gradle.util.DeprecationLogger;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class Wrapper {
    public static final String WRAPPER_PROPERTIES_PROPERTY = "org.gradle.wrapper.properties";

    public static final String DISTRIBUTION_URL_PROPERTY = "distributionUrl";
    public static final String DISTRIBUTION_BASE_PROPERTY = "distributionBase";
    public static final String ZIP_STORE_BASE_PROPERTY = "zipStoreBase";
    public static final String DISTRIBUTION_PATH_PROPERTY = "distributionPath";
    public static final String ZIP_STORE_PATH_PROPERTY = "zipStorePath";
    private final Properties properties;
    private final URI distribution;
    private final File propertiesFile;

    public Wrapper() {
        this(new File(System.getProperty(WRAPPER_PROPERTIES_PROPERTY)), new Properties());
    }

    public Wrapper(File projectDir) {
        this(new File(projectDir, "gradle/wrapper/gradle-wrapper.properties"), new Properties());
    }

    Wrapper(File propertiesFile, Properties properties) {
        this.properties = properties;
        this.propertiesFile = propertiesFile;
        if (propertiesFile.exists()) {
            try {
                loadProperties(propertiesFile, properties);
                distribution = readDistroUrl();
            } catch (Exception e) {
                throw new RuntimeException(String.format("Could not load wrapper properties from '%s'.", propertiesFile), e);
            }
        } else {
            distribution = null;
        }
    }

    private URI readDistroUrl() throws URISyntaxException {
        if (properties.getProperty(DISTRIBUTION_URL_PROPERTY) != null) {
            return new URI(getProperty(DISTRIBUTION_URL_PROPERTY));
        }
        //try the deprecated way:
        return readDistroUrlDeprecatedWay();
    }

    @Deprecated
    private URI readDistroUrlDeprecatedWay() throws URISyntaxException {
        String distroUrl = null;
        try {
        distroUrl = getProperty("urlRoot") + "/"
            + getProperty("distributionName") + "-"
            + getProperty("distributionVersion") + "-"
            + getProperty("distributionClassifier") + ".zip";
        DeprecationLogger.nagUserWith(propertiesFile + " contains deprecated entries: 'urlRoot', 'distributionName', 'distributionVersion' and 'distributionClassifier' are deprecated and will be removed soon. Please use '" + DISTRIBUTION_URL_PROPERTY + "' instead.");
    } catch (Exception e) {
        //even the deprecated properties are not provided, report error:
        reportMissingProperty(DISTRIBUTION_URL_PROPERTY);
    }
        return new URI(distroUrl);
    }

    private static void loadProperties(File propertiesFile, Properties properties) throws IOException {
        InputStream inStream = new FileInputStream(propertiesFile);
        try {
            properties.load(inStream);
        } finally {
            inStream.close();
        }
    }

    /**
     * Returns the distribution which this wrapper will use. Returns null if no wrapper meta-data was found in the specified project directory.
     */
    public URI getDistribution() {
        return distribution;
    }

    public void execute(String[] args, Install install, BootstrapMainStarter bootstrapMainStarter) throws Exception {
        if (distribution == null) {
            throw new FileNotFoundException(String.format("Wrapper properties file '%s' does not exist.", propertiesFile));
        }
        File gradleHome = install.createDist(
                getDistribution(),
                getProperty(DISTRIBUTION_BASE_PROPERTY),
                getProperty(DISTRIBUTION_PATH_PROPERTY),
                getProperty(ZIP_STORE_BASE_PROPERTY),
                getProperty(ZIP_STORE_PATH_PROPERTY)
        );
        bootstrapMainStarter.start(args, gradleHome);
    }

    private String getProperty(String propertyName) {
        String value = properties.getProperty(propertyName);
        if (value == null) {
            return reportMissingProperty(propertyName);
        }
        return value;
    }

    private String reportMissingProperty(String propertyName) {
        throw new RuntimeException(String.format(
                "No value with key '%s' specified in wrapper properties file '%s'.", propertyName, propertiesFile));
    }
}
