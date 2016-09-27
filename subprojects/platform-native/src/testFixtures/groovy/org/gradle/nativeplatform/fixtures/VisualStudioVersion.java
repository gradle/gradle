/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.nativeplatform.fixtures;

import org.gradle.util.VersionNumber;

public enum VisualStudioVersion {
    VISUALSTUDIO_2012("11.0", "2012"),
    VISUALSTUDIO_2013("12.0", "2013"),
    VISUALSTUDIO_2015("14.0", "2015");

    private final VersionNumber visualCppVersion;
    private final String version;

    VisualStudioVersion(String visualCppVersion, String version) {
        this.visualCppVersion = VersionNumber.parse(visualCppVersion);
        this.version = version;
    }

    public VersionNumber getVisualCppVersion() {
        return visualCppVersion;
    }

    public String getVersion() {
        return version;
    }
}
