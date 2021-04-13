/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.msvcpp;

import org.gradle.util.internal.VersionNumber;

public enum VisualStudioVersion {
    VISUALSTUDIO_2012("11.0", "2012"),
    VISUALSTUDIO_2013("12.0", "2013"),
    VISUALSTUDIO_2015("14.0", "2015"),
    VISUALSTUDIO_2017("15.0", "2017"),
    VISUALSTUDIO_2019("16.0", "2019");

    private final VersionNumber version;
    private final String year;

    VisualStudioVersion(String version, String year) {
        this.version = VersionNumber.parse(version);
        this.year = year;
    }

    public VersionNumber getVersion() {
        return version;
    }

    public String getYear() {
        return year;
    }
}
