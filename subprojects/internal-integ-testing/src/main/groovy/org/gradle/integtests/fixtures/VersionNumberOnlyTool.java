/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures;

import org.gradle.util.VersionNumber;

public class VersionNumberOnlyTool implements AbstractContextualMultiVersionSpecRunner.VersionedTool {
    private final String versionString;
    private final VersionNumber versionNumber;

    public VersionNumberOnlyTool(String version) {
        this.versionString = version;
        this.versionNumber = VersionNumber.parse(version);
    }

    public VersionNumberOnlyTool(VersionNumber versionNumber) {
        this.versionNumber = versionNumber;
        this.versionString = versionNumber.toString();
    }

    @Override
    public VersionNumber getVersion() {
        return versionNumber;
    }

    public String getVersionString() {
        return versionString;
    }

    @Override
    public boolean matches(String criteria) {
        return VersionNumber.parse(criteria).equals(versionNumber);
    }
}
