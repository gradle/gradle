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

package org.gradle.language.mirah.internal;

import org.gradle.language.mirah.ScalaPlatform;
import org.gradle.util.VersionNumber;

public class DefaultScalaPlatform implements ScalaPlatform {
    private final String mirahCompatibilityVersion;
    private final String mirahVersion;

    public DefaultScalaPlatform(String mirahVersion) {
        this(VersionNumber.parse(mirahVersion));
    }

    public DefaultScalaPlatform(VersionNumber versionNumber) {
        this.mirahVersion = versionNumber.getMajor() + "." + versionNumber.getMinor() + "." + versionNumber.getMicro();
        this.mirahCompatibilityVersion = versionNumber.getMajor() + "." + versionNumber.getMinor();
    }

    public String getScalaVersion() {
        return mirahVersion;
    }

    public String getScalaCompatibilityVersion() {
        return mirahCompatibilityVersion;
    }

    public String getDisplayName() {
        return String.format("Scala Platform (Scala %s)", mirahVersion);
    }

    public String getName() {
        return String.format("ScalaPlatform%s", mirahVersion);
    }
}
