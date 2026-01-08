/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.jvm

import spock.lang.Specification

class JpmsConfigurationTest extends Specification {

    def "forGroovyProcesses returns empty list for Java 8 and below"() {
        expect:
        JpmsConfiguration.forGroovyCompilerWorker(majorVersion) == []

        where:
        majorVersion << [1, 5, 6, 7, 8]
    }

    def "forGroovyProcesses returns JPMS args for Java 9 and above"() {
        when:
        def result = JpmsConfiguration.forGroovyCompilerWorker(majorVersion)

        then:
        result == [
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        ]

        where:
        majorVersion << [9, 11, 17, 21, 24, 25]
    }

    def "forWorkerProcesses returns empty list when native services are not used"() {
        expect:
        JpmsConfiguration.forWorkerProcesses(majorVersion, false) == []

        where:
        majorVersion << [8, 9, 17, 21, 24, 25]
    }

    def "forWorkerProcesses returns empty list for Java below 24 even with native services"() {
        expect:
        JpmsConfiguration.forWorkerProcesses(majorVersion, true) == []

        where:
        majorVersion << [8, 9, 11, 17, 21, 23]
    }

    def "forWorkerProcesses returns native access args for Java 24+ with native services"() {
        when:
        def result = JpmsConfiguration.forWorkerProcesses(majorVersion, true)

        then:
        result == ["--enable-native-access=ALL-UNNAMED"]

        where:
        majorVersion << [24, 25, 26]
    }

    def "forDaemonProcesses returns empty list for Java 8 and below"() {
        expect:
        JpmsConfiguration.forDaemonProcesses(majorVersion, false) == []

        where:
        majorVersion << [1, 5, 6, 7, 8]
    }

    def "forDaemonProcesses returns JPMS args for Java 9-23 without native services"() {
        when:
        def result = JpmsConfiguration.forDaemonProcesses(majorVersion, false)

        then:
        result == [
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
        ]

        where:
        majorVersion << [9, 11, 17, 21, 23]
    }

    def "forDaemonProcesses returns JPMS args for Java 9-23 with native services"() {
        when:
        def result = JpmsConfiguration.forDaemonProcesses(majorVersion, true)

        then:
        // Should be same as without native services for Java < 24
        result == [
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
        ]

        where:
        majorVersion << [9, 11, 17, 21, 23]
    }

    def "forDaemonProcesses returns JPMS args plus native access for Java 24+ with native services"() {
        when:
        def result = JpmsConfiguration.forDaemonProcesses(majorVersion, true)

        then:
        result == [
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
            "--enable-native-access=ALL-UNNAMED"
        ]

        where:
        majorVersion << [24, 25, 26]
    }

    def "forDaemonProcesses returns JPMS args only for Java 24+ without native services"() {
        when:
        def result = JpmsConfiguration.forDaemonProcesses(majorVersion, false)

        then:
        result == [
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
        ]

        where:
        majorVersion << [24, 25, 26]
    }
}
