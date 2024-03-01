/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.workers.internal

import spock.lang.Specification

class DaemonForkOptionsBuilderTest extends Specification {
    def "ignores other options"() {
        expect:
        !DaemonForkOptionsBuilder.findUnreliableArgument(["--show-version", "-ea", "pkg.Main"]).isPresent()
    }

    def "recognizes unreliable options in JVM args"() {
        expect:
        DaemonForkOptionsBuilder.findUnreliableArgument(options).get() == options[0]
        where:
        options << [
            ["-cp", "/path/to/jar"],
            ["-classpath", "/path/to/jar"],
            ["--class-path", "/path/to/jar"],
            ["-p", "/path/to/jar"],
            ["--module-path", "/path/to/jar"],
            ["--upgrade-module-path", "/path/to/jar"],
            ["--patch-module", "/path/to/jar"],
        ]
    }

    def "recognizes unreliable option prefixes in JVM args"() {
        expect:
        DaemonForkOptionsBuilder.findUnreliableArgument(options).get() == options[0]
        where:
        options << [
            ["-javaagent:/path/to/jar"],
            ["-javaagent", "/path/to/jar"],
            ["-agentpath:/path/to/jar"],
            ["-agentpath", "/path/to/jar"],
            ["-Xbootclasspath:/path/to/jar"],
            ["-Xbootclasspath", "path/to/jar"],
            ["-Xbootclasspath/a:/path/to/jar"],
            ["-Xbootclasspath/a", "/path/to/jar"],
        ]
    }
}
