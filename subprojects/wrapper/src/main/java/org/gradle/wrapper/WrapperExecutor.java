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

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Formatter;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class WrapperExecutor {
    public static final String DISTRIBUTION_URL_PROPERTY = "distributionUrl";
    public static final String DISTRIBUTION_BASE_PROPERTY = "distributionBase";
    public static final String ZIP_STORE_BASE_PROPERTY = "zipStoreBase";
    public static final String DISTRIBUTION_PATH_PROPERTY = "distributionPath";
    public static final String ZIP_STORE_PATH_PROPERTY = "zipStorePath";
    private final Properties properties;
    private final URI distribution;
    private final File propertiesFile;
    private final Appendable warningOutput;

    public static WrapperExecutor forProjectDirectory(File projectDir, Appendable warningOutput) {
        return new WrapperExecutor(new File(projectDir, "gradle/wrapper/gradle-wrapper.properties"), new Properties(), warningOutput);
    }

    public static WrapperExecutor forWrapperPropertiesFile(File propertiesFile, Appendable warningOutput) {
        return new WrapperExecutor(propertiesFile, new Properties(), warningOutput);
    }

    WrapperExecutor(File propertiesFile, Properties properties, Appendable warningOutput) {
        this.properties = properties;
        this.propertiesFile = propertiesFile;
        this.warningOutput = warningOutput;
        if (propertiesFile.exists()) {
            try {
                loadProperties(propertiesFile, properties);
                distribution = prepareDistributionUri();
            } catch (Exception e) {
                throw new RuntimeException(String.format("Could not load wrapper properties from '%s'.", propertiesFile), e);
            }
        } else {
            distribution = null;
        }
    }

    private URI prepareDistributionUri() throws URISyntaxException {
        URI source = readDistroUrl();
        if (source.getScheme() == null) {
            //no scheme means someone passed a relative url. In our context only file relative urls make sense.
            return new File(propertiesFile.getParentFile(), source.getSchemeSpecificPart()).toURI();
        } else {
            return source;
        }
    }

    private URI readDistroUrl() throws URISyntaxException {
        if (properties.getProperty(DISTRIBUTION_URL_PROPERTY) != null) {
            return new URI(getProperty(DISTRIBUTION_URL_PROPERTY));
        }
        //try the deprecated way:
        return readDistroUrlDeprecatedWay();
    }

    private URI readDistroUrlDeprecatedWay() throws URISyntaxException {
        String distroUrl = null;
        try {
            distroUrl = getProperty("urlRoot") + "/"
                    + getProperty("distributionName") + "-"
                    + getProperty("distributionVersion") + "-"
                    + getProperty("distributionClassifier") + ".zip";
            Formatter formatter = new Formatter();
            formatter.format("%s contains deprecated entries: 'urlRoot', 'distributionName', 'distributionVersion' and 'distributionClassifier' are deprecated and will be removed soon. Please use '%s' instead.%n", properties, DISTRIBUTION_URL_PROPERTY);
            warningOutput.append(formatter.toString());
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
