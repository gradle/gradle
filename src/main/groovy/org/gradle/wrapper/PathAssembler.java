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

/**
 * @author Hans Dockter
 */
public class PathAssembler {
    public static final String GRADLE_USER_HOME_STRING = "GRADLE_USER_HOME";
    public static final String PROJECT_STRING = "PROJECT";

    public String gradleHome(String distBase, String distPath, String distName, String distVersion) {
        return getBaseDir(distBase) + "/" + distPath + "/" + distName + "-" + distVersion;
    }

    public String distZip(String zipBase, String zipPath, String distName, String distVersion) {
        return getBaseDir(zipBase) + "/" + zipPath + "/" + distName + "-" + distVersion + ".zip";
    }

    private String getBaseDir(String base) {
        if (base.equals(GRADLE_USER_HOME_STRING)) {
            return System.getProperty("gradle.user.home");
        } else if (base.equals(PROJECT_STRING)) {
            return System.getProperty("user.dir");
        } else {
            throw new RuntimeException("Base: " + base + " is unknown");
        }
    }
}
