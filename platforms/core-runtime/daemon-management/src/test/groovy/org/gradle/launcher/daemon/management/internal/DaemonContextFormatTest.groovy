/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.daemon.management.internal

import org.gradle.util.GradleVersion
import spock.lang.Specification

class DaemonContextFormatTest extends Specification {

    def "Gradle #version wrote the #format layout"() {
        expect:
        DaemonContextFormat.forVersion(GradleVersion.version(version)) == format

        where:
        version                            | format
        "7.0"                              | DaemonContextFormat.V1
        "7.6.4"                            | DaemonContextFormat.V1
        "8.0.2"                            | DaemonContextFormat.V1
        "8.1"                              | DaemonContextFormat.V2
        "8.5"                              | DaemonContextFormat.V2
        "8.7"                              | DaemonContextFormat.V2
        "8.8"                              | DaemonContextFormat.V3
        "8.9"                              | DaemonContextFormat.V3
        "8.10"                             | DaemonContextFormat.V4
        "8.14.3"                           | DaemonContextFormat.V4
        "9.0.0"                            | DaemonContextFormat.V4
        "9.9.0"                            | DaemonContextFormat.V4
    }

    def "a pre-release is treated as the version it leads to"() {
        expect:
        DaemonContextFormat.forVersion(GradleVersion.version(version)) == format

        where:
        version                            | format
        "8.1-rc-1"                         | DaemonContextFormat.V2
        "8.8-milestone-1"                  | DaemonContextFormat.V3
        "8.10-rc-2"                        | DaemonContextFormat.V4
        "9.9.0-20260921020407+0000"        | DaemonContextFormat.V4
    }

    def "a version newer than any known layout is read with the newest one"() {
        expect:
        DaemonContextFormat.forVersion(GradleVersion.version("42.0")) == DaemonContextFormat.V4
    }
}
