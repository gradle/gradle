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
        JpmsConfiguration.forGroovyProcesses(majorVersion) == []

        where:
        majorVersion << [1, 5, 6, 7, 8]
    }

    def "forGroovyProcesses returns JPMS args for Java 9 and above"() {
        when:
        def result = JpmsConfiguration.forGroovyProcesses(majorVersion)

        then:
        result.size() == 6
        result.contains("--add-opens=java.base/java.lang=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.util=ALL-UNNAMED")
        result.contains("--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED")
        result.contains("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
        result.contains("--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")

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
        result.size() == 12
        // Groovy JPMS args
        result.contains("--add-opens=java.base/java.lang=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.util=ALL-UNNAMED")
        result.contains("--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED")
        result.contains("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
        result.contains("--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
        // Additional daemon JPMS args
        result.contains("--add-opens=java.base/java.nio.charset=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.net=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED")
        result.contains("--add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.time=ALL-UNNAMED")

        where:
        majorVersion << [9, 11, 17, 21, 23]
    }

    def "forDaemonProcesses returns JPMS args for Java 9-23 with native services"() {
        when:
        def result = JpmsConfiguration.forDaemonProcesses(majorVersion, true)

        then:
        // Should be same as without native services for Java < 24
        result.size() == 12
        result.contains("--add-opens=java.base/java.util=ALL-UNNAMED")
        !result.contains("--enable-native-access=ALL-UNNAMED")

        where:
        majorVersion << [9, 11, 17, 21, 23]
    }

    def "forDaemonProcesses returns JPMS args plus native access for Java 24+ with native services"() {
        when:
        def result = JpmsConfiguration.forDaemonProcesses(majorVersion, true)

        then:
        result.size() == 13
        // Groovy JPMS args
        result.contains("--add-opens=java.base/java.lang=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.util=ALL-UNNAMED")
        result.contains("--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED")
        // Additional daemon JPMS args
        result.contains("--add-opens=java.base/java.nio.charset=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.net=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED")
        result.contains("--add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED")
        result.contains("--add-opens=java.base/java.time=ALL-UNNAMED")
        // Native access flag
        result.contains("--enable-native-access=ALL-UNNAMED")

        where:
        majorVersion << [24, 25, 26]
    }

    def "forDaemonProcesses returns JPMS args only for Java 24+ without native services"() {
        when:
        def result = JpmsConfiguration.forDaemonProcesses(majorVersion, false)

        then:
        result.size() == 12
        !result.contains("--enable-native-access=ALL-UNNAMED")

        where:
        majorVersion << [24, 25, 26]
    }

    def "forTestWorkers returns empty list for Java 8 and below"() {
        expect:
        JpmsConfiguration.forTestWorkers(majorVersion) == []

        where:
        majorVersion << [1, 5, 6, 7, 8]
    }

    def "forTestWorkers returns JPMS args for Java 9 and above"() {
        when:
        def result = JpmsConfiguration.forTestWorkers(majorVersion)

        then:
        result.size() == 1
        result.contains("--add-opens=java.base/java.lang=ALL-UNNAMED")

        where:
        majorVersion << [9, 11, 17, 21, 24, 25]
    }

    def "forTestWorkersWithTestKit returns JPMS args for Java 9 and above"() {
        when:
        def result = JpmsConfiguration.forTestWorkersInJavaGradlePlugin(majorVersion)

        then:
        result.size() == 2
        result.contains("--add-opens=java.base/java.lang=ALL-UNNAMED")
        result.contains("--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED")

        where:
        majorVersion << [9, 11, 17, 21, 24, 25]
    }

    def "forTestWorkersWithTestKit returns empty list for Java 8 and below"() {
        expect:
        JpmsConfiguration.forTestWorkersInJavaGradlePlugin(majorVersion) == []

        where:
        majorVersion << [1, 5, 6, 7, 8]
    }

    def "all methods return immutable lists"() {
        when:
        def groovyResult = JpmsConfiguration.forGroovyProcesses(9)
        def workerResult = JpmsConfiguration.forWorkerProcesses(24, true)
        def daemonResult = JpmsConfiguration.forDaemonProcesses(9, false)
        def testWorkerResult = JpmsConfiguration.forTestWorkers(9)
        def testWorkerWithTestKitResult = JpmsConfiguration.forTestWorkersInJavaGradlePlugin(9)

        then:
        // Attempting to modify should throw UnsupportedOperationException
        [groovyResult, workerResult, daemonResult, testWorkerResult, testWorkerWithTestKitResult].each { list ->
            try {
                list.add("--some-arg")
                assert false, "Expected UnsupportedOperationException"
            } catch (UnsupportedOperationException e) {
                // Expected
            }
        }
    }

    def "forTestWorkers returns consistent results across versions"() {
        expect:
        JpmsConfiguration.forTestWorkers(9) == JpmsConfiguration.forTestWorkers(17)
        JpmsConfiguration.forTestWorkers(17) == JpmsConfiguration.forTestWorkers(24)
    }

    def "forTestWorkersWithTestKit returns consistent results across versions"() {
        expect:
        JpmsConfiguration.forTestWorkersInJavaGradlePlugin(9) == JpmsConfiguration.forTestWorkersInJavaGradlePlugin(17)
        JpmsConfiguration.forTestWorkersInJavaGradlePlugin(17) == JpmsConfiguration.forTestWorkersInJavaGradlePlugin(24)
    }

    def "all JPMS args lists are non-null"() {
        expect:
        JpmsConfiguration.forGroovyProcesses(version) != null
        JpmsConfiguration.forWorkerProcesses(version, nativeServices) != null
        JpmsConfiguration.forDaemonProcesses(version, nativeServices) != null
        JpmsConfiguration.forTestWorkers(version) != null
        JpmsConfiguration.forTestWorkersInJavaGradlePlugin(version) != null

        where:
        version | nativeServices
        8       | false
        8       | true
        9       | false
        9       | true
        17      | false
        17      | true
        24      | false
        24      | true
    }

    def "forTestWorkers contains subset of forTestWorkersWithTestKit args"() {
        when:
        def testWorkersArgs = JpmsConfiguration.forTestWorkers(9)
        def testWorkersWithTestKitArgs = JpmsConfiguration.forTestWorkersInJavaGradlePlugin(9)

        then:
        testWorkersWithTestKitArgs.containsAll(testWorkersArgs)
        testWorkersWithTestKitArgs.size() > testWorkersArgs.size()
    }
}
