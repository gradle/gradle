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

import java.io.IOException;
import java.io.File;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.net.URLClassLoader;
import java.net.URL;
import java.lang.reflect.Method;

/**
 * @author Hans Dockter
 */
public class Wrapper {
    public static final String URL_ROOT_PROPERTY = "urlRoot";
    public static final String DISTRIBUTION_BASE_PROPERTY = "distributionBase";
    public static final String ZIP_STORE_BASE_PROPERTY = "zipStoreBase";
    public static final String DISTRIBUTION_PATH_PROPERTY = "distributionPath";
    public static final String DISTRIBUTION_VERSION_PROPERTY = "distributionVersion";
    public static final String ZIP_STORE_PATH_PROPERTY = "zipStorePath";
    public static final String DISTRIBUTION_NAME_PROPERTY = "distributionName";
    
    public void execute(String[] args, Install install, BootstrapMainStarter bootstrapMainStarter) throws Exception, InterruptedException {
        Properties wrapperProperties = new Properties();
        wrapperProperties.load(getClass().getResourceAsStream("/org/gradle/wrapper/wrapper.properties"));
        String version = (String) wrapperProperties.get(DISTRIBUTION_VERSION_PROPERTY);
        String gradleHome = install.createDist(
                (String) wrapperProperties.get(URL_ROOT_PROPERTY),
                (String) wrapperProperties.get(DISTRIBUTION_BASE_PROPERTY),
                (String) wrapperProperties.get(DISTRIBUTION_PATH_PROPERTY),
                (String) wrapperProperties.get(DISTRIBUTION_NAME_PROPERTY),
                version,
                (String) wrapperProperties.get(ZIP_STORE_BASE_PROPERTY),
                (String) wrapperProperties.get(ZIP_STORE_PATH_PROPERTY)
        );
        bootstrapMainStarter.start(args, gradleHome, version);
    }
}
