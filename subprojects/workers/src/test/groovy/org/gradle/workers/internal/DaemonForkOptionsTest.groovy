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

package org.gradle.workers.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.process.JavaForkOptions
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.execFactory
import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class DaemonForkOptionsTest extends Specification {
    def "is compatible with itself"() {
        def settings = daemonForkOptionsBuilder()
            .classpath([new File("lib/lib1.jar"), new File("lib/lib2.jar")])
            .sharedPackages(["foo.bar", "foo.baz"])
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()

        expect:
        settings.isCompatibleWith(settings)
    }

    def "is compatible with same settings"() {
        def settings1 = daemonForkOptionsBuilder()
            .classpath([new File("lib/lib1.jar"), new File("lib/lib2.jar")])
            .sharedPackages(["foo.bar", "foo.baz"])
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .classpath([new File("lib/lib1.jar"), new File("lib/lib2.jar")])
            .sharedPackages(["foo.bar", "foo.baz"])
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is compatible with same class path"() {
        def settings1 = daemonForkOptionsBuilder()
            .classpath([new File("lib/lib1.jar"), new File("lib/lib2.jar")])
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .classpath([new File("lib/lib1.jar"), new File("lib/lib2.jar")])
            .build()

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is compatible with subset of class path"() {
        def settings1 = daemonForkOptionsBuilder()
            .classpath([new File("lib/lib1.jar"), new File("lib/lib2.jar")])
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .classpath([new File("lib/lib1.jar")])
            .build()

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is not compatible with different class path"() {
        def settings1 = daemonForkOptionsBuilder()
            .classpath([new File("lib/lib1.jar"), new File("lib/lib2.jar")])
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .classpath([new File("lib/lib1.jar"), new File("lib/lib3.jar")])
            .build()

        expect:
        !settings1.isCompatibleWith(settings2)
    }

    def "is compatible with same set of shared packages"() {
        def settings1 = daemonForkOptionsBuilder()
            .sharedPackages(["foo.bar", "foo.baz"])
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .sharedPackages(["foo.bar", "foo.baz"])
            .build()

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is compatible with subset of shared packages"() {
        def settings1 = daemonForkOptionsBuilder()
            .sharedPackages(["foo.bar", "foo.baz"])
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .sharedPackages(["foo.bar"])
            .build()

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is not compatible with different set of shared packages"() {
        def settings1 = daemonForkOptionsBuilder()
            .sharedPackages(["foo.bar", "foo.baz"])
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .sharedPackages(["bar.foo", "foo.baz"])
            .build()

        expect:
        !settings1.isCompatibleWith(settings2)
    }

    def "is compatible with same keep alive modes"() {
        def settings1 = daemonForkOptionsBuilder()
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is not compatible with different keep alive modes"() {
        def settings1 = daemonForkOptionsBuilder()
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .keepAliveMode(KeepAliveMode.DAEMON)
            .build()

        expect:
        !settings1.isCompatibleWith(settings2)
    }

    def "unspecified class path and shared packages default to empty list"() {
        when:
        def options = daemonForkOptionsBuilder().build()

        then:
        options.classpath == []
        options.sharedPackages == []
    }

    def "unspecified keepAlive mode defaults to DAEMON"() {
        when:
        def options = daemonForkOptionsBuilder().build()

        then:
        options.keepAliveMode == KeepAliveMode.DAEMON
    }

    def "is compatible with compatible java forkOptions"() {
        def javaForkOptions = TestFiles.execFactory().newJavaForkOptions()
        javaForkOptions.workingDir = systemSpecificAbsolutePath("foo")
        javaForkOptions.minHeapSize = "128m"
        javaForkOptions.maxHeapSize = "1g"
        javaForkOptions.jvmArgs = ["-server", "-verbose:gc"]
        def settings1 = daemonForkOptionsBuilder(javaForkOptions)
            .build()
        def settings2 = daemonForkOptionsBuilder(javaForkOptions)
            .build()

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is not compatible with incompatible java forkOptions"() {
        def javaForkOptions1 = TestFiles.execFactory().newJavaForkOptions()
        javaForkOptions1.workingDir = systemSpecificAbsolutePath("foo")
        javaForkOptions1.minHeapSize = "128m"
        javaForkOptions1.maxHeapSize = "1g"
        javaForkOptions1.jvmArgs = ["-server", "-verbose:gc"]
        def javaForkOptions2 = TestFiles.execFactory().newJavaForkOptions()
        javaForkOptions2.workingDir = systemSpecificAbsolutePath("foo")
        javaForkOptions2.minHeapSize = "256m"
        javaForkOptions2.maxHeapSize = "1g"
        javaForkOptions2.jvmArgs = ["-server", "-verbose:gc"]
        def settings1 = daemonForkOptionsBuilder(javaForkOptions1)
            .build()
        def settings2 = daemonForkOptionsBuilder(javaForkOptions2)
            .build()

        expect:
        !settings1.isCompatibleWith(settings2)
    }

    DaemonForkOptionsBuilder daemonForkOptionsBuilder() {
        def javaForkOptions = TestFiles.execFactory().newJavaForkOptions()
        javaForkOptions.workingDir = systemSpecificAbsolutePath("foo")
        return daemonForkOptionsBuilder(javaForkOptions)
    }

    DaemonForkOptionsBuilder daemonForkOptionsBuilder(JavaForkOptions javaForkOptions) {
        return new DaemonForkOptionsBuilder(execFactory()).javaForkOptions(javaForkOptions)
    }
}
