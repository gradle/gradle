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

import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.scala.ScalaPlatform;
import org.gradle.play.internal.platform.PlayPlatformInternal;

public class DefaultPlayPlatform implements PlayPlatformInternal {
    public final static String DEFAULT_PLAY_VERSION = "2.6.15";
    private final String playVersion;
    private final ScalaPlatform scalaPlatform;
    private final JavaPlatform javaPlatform;
    private final String name;

    public DefaultPlayPlatform(String name, String playVersion, ScalaPlatform scalaPlatform, JavaPlatform javaPlatform) {
        this.name = name;
        this.playVersion = playVersion;
        this.scalaPlatform = scalaPlatform;
        this.javaPlatform = javaPlatform;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return "Play Platform (Play " + playVersion + ", Scala: " + scalaPlatform.getScalaCompatibilityVersion() + ", Java: " + javaPlatform.getDisplayName() + ")";
    }

    @Override
    public String getPlayVersion() {
        return playVersion;
    }

    @Override
    public ScalaPlatform getScalaPlatform() {
        return scalaPlatform;
    }

    @Override
    public JavaPlatform getJavaPlatform() {
        return javaPlatform;
    }

    @Override
    public String getDependencyNotation(String playModule) {
        return "com.typesafe.play:" + playModule + "_" + scalaPlatform.getScalaCompatibilityVersion() + ":" + playVersion;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
