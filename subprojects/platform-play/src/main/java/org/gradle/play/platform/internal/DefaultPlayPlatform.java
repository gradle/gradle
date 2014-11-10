/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.platform.internal;

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.play.platform.PlayPlatform;

public class DefaultPlayPlatform implements PlayPlatform {
    private final String playVersion;
    private String scalaVersion;
    private final JavaVersion javaVersion;

    public DefaultPlayPlatform(String playVersion, JavaVersion javaVersion) {
        this.playVersion = playVersion;
        this.scalaVersion = calculateDefaultScalaVersion(playVersion);
        this.javaVersion = javaVersion;
    }

    private String calculateDefaultScalaVersion(String playVersion) {
        if(playVersion.startsWith("2.2")){
            return "2.10";
        }else if(playVersion.startsWith("2.3")){
            return "2.11";
        }else {
            throw new GradleException("Unsupported Play Version");
        }
    }

    public String getPlayVersion() {
        return playVersion;
    }

    public String getScalaVersion() {
        return scalaVersion;
    }

    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    public String getName() {
        return String.format("PlayPlatform%s", playVersion);
    }

    public String getDisplayName() {
        return String.format("Play Platform (Play %s, Scala: %s, JDK %s (%s))", playVersion, scalaVersion, javaVersion.getMajorVersion(), javaVersion);
    }

}
