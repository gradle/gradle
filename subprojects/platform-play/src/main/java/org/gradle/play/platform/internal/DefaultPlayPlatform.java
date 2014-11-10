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

import org.gradle.api.JavaVersion;
import org.gradle.play.platform.PlayPlatform;

public class DefaultPlayPlatform implements PlayPlatform {
    private final String playVersion;
    private final JavaVersion javaVersion;

    public DefaultPlayPlatform(String playVersion, JavaVersion javaVersion) {
        this.playVersion = playVersion;
        this.javaVersion = javaVersion;
    }

    public String getPlayVersion() {
        return playVersion;
    }

    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    public String getName() {
        return String.format("PlayFramework%s", playVersion);
    }

    public String getDisplayName() {
        return String.format("Play Framework %s (JDK %s (%s))", playVersion, javaVersion.getMajorVersion(), javaVersion);
    }

}
