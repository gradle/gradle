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
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hans Dockter
 */
public class PathAssembler {
    public static final String GRADLE_USER_HOME_STRING = "GRADLE_USER_HOME";
    public static final String PROJECT_STRING = "PROJECT";

    private File gradleUserHome;

    public PathAssembler() {
    }

    public PathAssembler(File gradleUserHome) {
        this.gradleUserHome = gradleUserHome;
    }

    public File gradleHome(String distBase, String distPath, URI distUrl) {
        return new File(getBaseDir(distBase), distPath + "/" + getDistHome(distUrl));
    }

    public File distZip(String zipBase, String zipPath, URI distUrl) {
        return new File(getBaseDir(zipBase), zipPath + "/" + getDistName(distUrl));
    }

    private String getDistHome(URI distUrl) {
        String name = getDistName(distUrl);
        Matcher matcher = Pattern.compile("(\\p{Alpha}+-\\d+\\.\\d+.*?)(-\\p{Alpha}+)?\\.zip").matcher(name);
        if (!matcher.matches()) {
            throw new RuntimeException(String.format("Cannot determine Gradle version from distribution URL '%s'.",
                    distUrl));
        }
        return matcher.group(1);
    }

    private String getDistName(URI distUrl) {
        String path = distUrl.getPath();
        int p = path.lastIndexOf("/");
        if (p < 0) {
            return path;
        }
        return path.substring(p + 1);
    }

    private File getBaseDir(String base) {
        if (base.equals(GRADLE_USER_HOME_STRING)) {
            return gradleUserHome;
        } else if (base.equals(PROJECT_STRING)) {
            return new File(System.getProperty("user.dir"));
        } else {
            throw new RuntimeException("Base: " + base + " is unknown");
        }
    }
}
