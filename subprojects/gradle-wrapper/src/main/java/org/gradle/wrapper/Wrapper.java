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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
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

    public void execute(String[] args, Install install, BootstrapMainStarter bootstrapMainStarter) throws Exception {
        Properties wrapperProperties = new Properties();
        InputStream inStream = new FileInputStream(getWrapperPropertiesFile());
        try {
            wrapperProperties.load(inStream);
        } finally {
            inStream.close();
        }
        if (GradleWrapperMain.isDebug()) {
            System.out.println("wrapperProperties = " + wrapperProperties);
        }
        File gradleHome = install.createDist(
                new URI(getProperty(wrapperProperties, DISTRIBUTION_URL_PROPERTY)),
                getProperty(wrapperProperties, DISTRIBUTION_BASE_PROPERTY),
                getProperty(wrapperProperties, DISTRIBUTION_PATH_PROPERTY),
                getProperty(wrapperProperties, ZIP_STORE_BASE_PROPERTY),
                getProperty(wrapperProperties, ZIP_STORE_PATH_PROPERTY)
        );
        if (GradleWrapperMain.isDebug()) {
            System.out.println("args = " + Arrays.asList(args));
        }
        bootstrapMainStarter.start(args, gradleHome);
    }

    private File getWrapperPropertiesFile() {
        return new File(System.getProperty(WRAPPER_PROPERTIES_PROPERTY));
    }

    private String getProperty(Properties wrapperProperties, String propertyName) {
        String value = wrapperProperties.getProperty(propertyName);
        if (value == null) {
            throw new RuntimeException(String.format(
                    "No value with key '%s' specified in wrapper properties file '%s'.", propertyName,
                    getWrapperPropertiesFile()));
        }
        return value;
    }
}
