/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.util;

import groovy.lang.GroovySystem;
import org.apache.ivy.Ivy;
import org.apache.tools.ant.Main;

import java.util.Properties;

/**
 * @author Hans Dockter
 * @author Russel Winder
 */
public class GradleVersion {
    private final static String BUILD_TIME = "buildTime";
    private final static String VERSION = "version";
    private final static String FILE_NAME = "/org/gradle/version.properties";
    public final static String URL = "http://www.gradle.org";

    private final Properties versionProperties;

    public GradleVersion() {
        versionProperties = GUtil.loadProperties(getClass().getResourceAsStream(FILE_NAME));
    }

    public String getVersion() {
        return versionProperties.getProperty(VERSION);
    }

    public String getBuildTime() {
        return versionProperties.getProperty(BUILD_TIME);
    }

    public String prettyPrint() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n------------------------------------------------------------\nGradle ");
        sb.append(getVersion());
        sb.append("\n------------------------------------------------------------\n\nGradle build time: ");
        sb.append(getBuildTime());
        sb.append("\nGroovy: ");
        sb.append(GroovySystem.getVersion());
        sb.append("\nAnt: ");
        sb.append(Main.getAntVersion());
        sb.append("\nIvy: ");
        sb.append(Ivy.getIvyVersion());
        sb.append("\nJVM: ");
        sb.append(Jvm.current());
        sb.append("\nOS: ");
        sb.append(OperatingSystem.current());
        sb.append("\n");
        return sb.toString();
    }
}
