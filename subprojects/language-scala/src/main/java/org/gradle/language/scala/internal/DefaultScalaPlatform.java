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

package org.gradle.language.scala.internal;

import org.gradle.language.scala.ScalaPlatform;
import org.gradle.util.VersionNumber;

public class DefaultScalaPlatform implements ScalaPlatform {
    private final String scalaCompatibilityVersion;
    private final String scalaVersion;

    public DefaultScalaPlatform() {
        this("2.10.4"); // default Scala version
    }

    public DefaultScalaPlatform(String scalaVersion) {
        this(VersionNumber.parse(scalaVersion));
    }

    public DefaultScalaPlatform(VersionNumber versionNumber) {
        this.scalaVersion = versionNumber.getMajor() + "." + versionNumber.getMinor() + "." + versionNumber.getMicro();
        this.scalaCompatibilityVersion = versionNumber.getMajor() + "." + versionNumber.getMinor();
    }

    @Override
    public String getScalaVersion() {
        return scalaVersion;
    }

    @Override
    public String getScalaCompatibilityVersion() {
        return scalaCompatibilityVersion;
    }

    @Override
    public String getDisplayName() {
        return "Scala Platform (Scala " + scalaVersion + ")";
    }

    @Override
    public String getName() {
        return "ScalaPlatform" + scalaVersion;
    }
}
