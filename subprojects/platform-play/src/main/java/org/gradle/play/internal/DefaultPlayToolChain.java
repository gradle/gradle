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

package org.gradle.play.internal;

import org.gradle.api.JavaVersion;
import org.gradle.play.PlayToolChain;

public class DefaultPlayToolChain implements PlayToolChain {
    private String playVersion;
    private final String scalaVersion;
    private JavaVersion javaVersion;

    public DefaultPlayToolChain(String playVersion, String scalaVersion) {
        this.playVersion = playVersion;
        this.scalaVersion = scalaVersion;
        this.javaVersion = JavaVersion.current();
    }

    public String getName() {
        return String.format("PlayFramework%s", playVersion);
    }

    public String getDisplayName() {
        return String.format("Play Framework %s / Scala %s / JDK %s (%s)", this.playVersion, this.scalaVersion, javaVersion.getMajorVersion(), javaVersion);
    }
}
