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

package org.gradle.build

import com.google.common.base.Preconditions
import groovy.transform.Canonical
import org.gradle.util.GradleVersion

import java.text.SimpleDateFormat

@Canonical
class ReleasedVersion {
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat('yyyyMMddHHmmssZ')
    static {
        TIMESTAMP_FORMAT.timeZone = TimeZone.getTimeZone("UTC")
    }
    final GradleVersion version
    final Date buildTimeStamp

    ReleasedVersion(String version, String buildTimeStamp) {
        Preconditions.checkNotNull(version, "version")
        Preconditions.checkNotNull(buildTimeStamp, "buildTimeStamp")
        this.version = GradleVersion.version(version)
        this.buildTimeStamp = TIMESTAMP_FORMAT.parse(buildTimeStamp)
    }

    Map<String, String> asMap() {
        [version: version.version, buildTime: TIMESTAMP_FORMAT.format(buildTimeStamp)]
    }

    static ReleasedVersion fromMap(Map<String, String> json) {
        new ReleasedVersion(json.version, json.buildTime)
    }

    boolean isFinalRelease() {
        version.baseVersion == version
    }
}
