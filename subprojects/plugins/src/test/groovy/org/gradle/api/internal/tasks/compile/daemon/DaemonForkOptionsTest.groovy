/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.daemon

import spock.lang.Specification
import org.gradle.api.tasks.compile.ForkOptions

class DaemonForkOptionsTest extends Specification {
    def "can be created from ForkOptions"() {
        def options = new ForkOptions()
        options.memoryInitialSize = "128m"
        options.memoryMaximumSize = "1g"
        options.jvmArgs = ["-server", "-esa"]
        options.tempDir = "/tmp" // has no equivalent

        when:
        def settings = new DaemonForkOptions(options)

        then:
        settings.minHeapSize == "128m"
        settings.maxHeapSize == "1g"
        settings.jvmArgs == ["-server", "-esa"]
    }
    
    def "is compatible with itself"() {
        def settings = new DaemonForkOptions("128m", "1g", ["-server", "-esa"])

        expect:
        settings.isCompatibleWith(settings)
    }

    def "is compatible with different representation of same memory requirements"() {
        def settings1 = new DaemonForkOptions("128m", "1g", ["-server", "-esa"])
        def settings2 = new DaemonForkOptions("128m", "1024m", ["-server", "-esa"])

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is compatible with lower memory requirements"() {
        def settings1 = new DaemonForkOptions("128m", "1g", ["-server", "-esa"])
        def settings2 = new DaemonForkOptions("64m", "512m", ["-server", "-esa"])

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is not compatible with higher memory requirements"() {
        def settings1 = new DaemonForkOptions("128m", "1g", ["-server", "-esa"])
        def settings2 = new DaemonForkOptions("256m", "512m", ["-server", "-esa"])

        expect:
        !settings1.isCompatibleWith(settings2)
    }

    def "is compatible with same set of JVM args"() {
        def settings1 = new DaemonForkOptions("128m", "1g", ["-server", "-esa"])
        def settings2 = new DaemonForkOptions("128m", "1m", ["-esa", "-server"])

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is not compatible with different set of JVM args"() {
        def settings1 = new DaemonForkOptions("128m", "1g", ["-server", "-esa"])
        def settings2 = new DaemonForkOptions("128m", "1g", ["-client", "-esa"])

        expect:
        !settings1.isCompatibleWith(settings2)
    }

    def "string values are trimmed"() {
        def settings1 = new DaemonForkOptions("128m ", "1g", [" -server", "-esa"])
        def settings2 = new DaemonForkOptions("128m", " 1g", ["-server", "-esa "])

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "capitalization of memory options is irrelevant"() {
        def settings1 = new DaemonForkOptions("128M", "1g", ["-server", "-esa"])
        def settings2 = new DaemonForkOptions("128m", "1G", ["-server", "-esa"])

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "capitalization of JVM args is relevant"() {
        def settings1 = new DaemonForkOptions("128m", "1g", ["-Server", "-esa"])
        def settings2 = new DaemonForkOptions("128m", "1g", ["-server", "-esa"])

        expect:
        !settings1.isCompatibleWith(settings2)
    }
}
