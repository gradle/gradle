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
package org.gradle.tooling.internal.consumer.daemon

import spock.lang.Specification
import spock.lang.Unroll

class DaemonContextReadersDispatchTest extends Specification {

    @Unroll
    def "version #version maps to #expected"() {
        expect:
        DaemonContextReaders.forVersion(version).class.simpleName == expected

        where:
        version            || expected
        "5.0"              || "DaemonContextReaderV1"
        "5.6.4"            || "DaemonContextReaderV1"
        "7.6"              || "DaemonContextReaderV1"
        "8.2.1"            || "DaemonContextReaderV1"
        "8.3"              || "DaemonContextReaderV2"
        "8.3.0"            || "DaemonContextReaderV2"
        "8.7"              || "DaemonContextReaderV2"
        "8.7.1"            || "DaemonContextReaderV2"
        "8.8"              || "DaemonContextReaderV3"
        "8.8.0"            || "DaemonContextReaderV3"
        "8.9.2"            || "DaemonContextReaderV3"
        "8.10"             || "DaemonContextReaderV4"
        "8.10.0"           || "DaemonContextReaderV4"
        "8.14"             || "DaemonContextReaderV4"
        "9.0.0"            || "DaemonContextReaderV4"
        "9.5"              || "DaemonContextReaderV4"
    }

    def "handles pre-release qualifiers via base version"() {
        expect:
        DaemonContextReaders.forVersion("8.8-rc-1").class.simpleName == "DaemonContextReaderV3"
        DaemonContextReaders.forVersion("8.10-20240920100000+0000").class.simpleName == "DaemonContextReaderV4"
    }
}
