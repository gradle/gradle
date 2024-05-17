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

import org.gradle.util.internal.WrapperDistributionUrlConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

public class WrapperExecutor {
    public static final String DISTRIBUTION_URL_PROPERTY = "distributionUrl";
    public static final String DISTRIBUTION_BASE_PROPERTY = "distributionBase";
    public static final String DISTRIBUTION_PATH_PROPERTY = "distributionPath";
    public static final String DISTRIBUTION_SHA_256_SUM = "distributionSha256Sum";
    public static final String ZIP_STORE_BASE_PROPERTY = "zipStoreBase";
    public static final String ZIP_STORE_PATH_PROPERTY = "zipStorePath";
    public static final String NETWORK_TIMEOUT_PROPERTY = "networkTimeout";
    public static final String VALIDATE_DISTRIBUTION_URL = "validateDistributionUrl";
    private final Properties properties;
    private final File propertiesFile;
    private final WrapperConfiguration config = new WrapperConfiguration();

    public static WrapperExecutor forProjectDirectory(File projectDir) {
        return new WrapperExecutor(new File(projectDir, "gradle/wrapper/gradle-wrapper.properties"), new Properties());
    }

    public static WrapperExecutor forWrapperPropertiesFile(File propertiesFile) {
        if (!propertiesFile.exists()) {
            throw new RuntimeException(String.format("Wrapper properties file '%s' does not exist.", propertiesFile));
        }
        return new WrapperExecutor(propertiesFile, new Properties());
    }

    WrapperExecutor(File propertiesFile, Properties properties) {
        this.properties = properties;
        this.propertiesFile = propertiesFile;
        if (propertiesFile.exists()) {
            try {
                loadProperties(propertiesFile, properties);
                config.setDistribution(WrapperDistributionUrlConverter.convertDistributionUrl(readDistroUrl(), propertiesFile.getParentFile()));
                config.setDistributionBase(getProperty(DISTRIBUTION_BASE_PROPERTY, config.getDistributionBase()));
                config.setDistributionPath(getProperty(DISTRIBUTION_PATH_PROPERTY, config.getDistributionPath()));
                config.setDistributionSha256Sum(getProperty(DISTRIBUTION_SHA_256_SUM, config.getDistributionSha256Sum(), false));
                config.setZipBase(getProperty(ZIP_STORE_BASE_PROPERTY, config.getZipBase()));
                config.setZipPath(getProperty(ZIP_STORE_PATH_PROPERTY, config.getZipPath()));
                config.setNetworkTimeout(getProperty(NETWORK_TIMEOUT_PROPERTY, config.getNetworkTimeout()));
                config.setValidateDistributionUrl(getProperty(VALIDATE_DISTRIBUTION_URL, config.getValidateDistributionUrl()));
            } catch (Exception e) {
                throw new RuntimeException(String.format("Could not load wrapper properties from '%s'.", propertiesFile), e);
            }
        }
    }

    private String readDistroUrl() {
        if (properties.getProperty(DISTRIBUTION_URL_PROPERTY) == null) {
            reportMissingProperty(DISTRIBUTION_URL_PROPERTY);
        }
        return getProperty(DISTRIBUTION_URL_PROPERTY);
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
        return config.getDistribution();
    }

    /**
     * Returns the configuration for this wrapper.
     */
    public WrapperConfiguration getConfiguration() {
        return config;
    }

    public void execute(String[] args, Install install, BootstrapMainStarter bootstrapMainStarter) throws Exception {
        File gradleHome = install.createDist(config);
        bootstrapMainStarter.start(args, gradleHome);
    }

    private String getProperty(String propertyName) {
        return getProperty(propertyName, null, true);
    }

    private String getProperty(String propertyName, String defaultValue) {
        return getProperty(propertyName, defaultValue, true);
    }

    private int getProperty(String propertyName, int defaultValue) {
        return Integer.parseInt(getProperty(propertyName, String.valueOf(defaultValue)));
    }

    private boolean getProperty(String propertyName, boolean defaultValue) {
        return Boolean.parseBoolean(getProperty(propertyName, String.valueOf(defaultValue)));
    }

    private String getProperty(String propertyName, String defaultValue, boolean required) {
        String value = properties.getProperty(propertyName);
        if (value != null) {
            return value;
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        if (required) {
            return reportMissingProperty(propertyName);
        } else {
            return null;
        }
    }

    private String reportMissingProperty(String propertyName) {
        throw new RuntimeException(String.format(
                "No value with key '%s' specified in wrapper properties file '%s'.", propertyName, propertiesFile));
    }
}
