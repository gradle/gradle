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
import org.gradle.internal.classloader.ClassLoaderSpec
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.ExecFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class DaemonForkOptionsTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());

    private ExecFactory execFactory = TestFiles.execFactory(tmpDir.testDirectory)

    def "is compatible with itself"() {
        def spec1 = Mock(ClassLoaderSpec)
        def spec2 = Mock(ClassLoaderSpec)
        def settings = daemonForkOptionsBuilder()
            .withClassLoaderStructure(new HierarchicalClassLoaderStructure(spec1).withChild(spec2))
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()

        expect:
        settings.isCompatibleWith(settings)
    }

    def "is compatible with same settings"() {
        def spec1 = Mock(ClassLoaderSpec)
        def spec2 = Mock(ClassLoaderSpec)
        def settings1 = daemonForkOptionsBuilder()
            .withClassLoaderStructure(new HierarchicalClassLoaderStructure(spec1).withChild(spec2))
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()
        def settings2 = daemonForkOptionsBuilder()
            .withClassLoaderStructure(new HierarchicalClassLoaderStructure(spec1).withChild(spec2))
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()

        expect:
        settings1.isCompatibleWith(settings2)
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

    def "is not compatible with different classloader structures"() {
        def spec1 = Mock(ClassLoaderSpec)
        def spec2 = Mock(ClassLoaderSpec)
        def spec3 = Mock(ClassLoaderSpec)
        def spec4 = Mock(ClassLoaderSpec)
        def settings1 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(new HierarchicalClassLoaderStructure(spec1).withChild(spec2).withChild(spec3))
                .build()
        def settings2 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(new HierarchicalClassLoaderStructure(spec1).withChild(spec2).withChild(spec4))
                .build()

        expect:
        !settings1.isCompatibleWith(settings2)
    }

    def "is compatible with the same classloader structure"() {
        def spec1 = Mock(ClassLoaderSpec)
        def spec2 = Mock(ClassLoaderSpec)
        def spec3 = Mock(ClassLoaderSpec)
        def settings1 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(new HierarchicalClassLoaderStructure(spec1).withChild(spec2).withChild(spec3))
                .build()
        def settings2 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(new HierarchicalClassLoaderStructure(spec1).withChild(spec2).withChild(spec3))
                .build()

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is compatible when classloader structures are null"() {
        def settings1 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(null)
                .build()
        def settings2 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(null)
                .build()

        expect:
        settings1.isCompatibleWith(settings2)
    }

    def "is not compatible when one classloader structure is null"() {
        when:
        def settings1 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(null)
                .build()
        def settings2 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(new HierarchicalClassLoaderStructure(Mock(ClassLoaderSpec)))
                .build()

        then:
        !settings1.isCompatibleWith(settings2)

        when:
        settings1 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(new HierarchicalClassLoaderStructure(Mock(ClassLoaderSpec)))
                .build()
        settings2 = daemonForkOptionsBuilder()
                .withClassLoaderStructure(null)
                .build()

        then:
        !settings1.isCompatibleWith(settings2)
    }

    def "unspecified keepAlive mode defaults to DAEMON"() {
        when:
        def options = daemonForkOptionsBuilder().build()

        then:
        options.keepAliveMode == KeepAliveMode.DAEMON
    }

    def "is compatible with compatible java forkOptions"() {
        def javaForkOptions = execFactory.newJavaForkOptions()
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
        def javaForkOptions1 = execFactory.newJavaForkOptions()
        javaForkOptions1.workingDir = systemSpecificAbsolutePath("foo")
        javaForkOptions1.minHeapSize = "128m"
        javaForkOptions1.maxHeapSize = "1g"
        javaForkOptions1.jvmArgs = ["-server", "-verbose:gc"]
        def javaForkOptions2 = execFactory.newJavaForkOptions()
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
        def javaForkOptions = execFactory.newJavaForkOptions()
        javaForkOptions.workingDir = systemSpecificAbsolutePath("foo")
        return daemonForkOptionsBuilder(javaForkOptions)
    }

    DaemonForkOptionsBuilder daemonForkOptionsBuilder(JavaForkOptions javaForkOptions) {
        return new DaemonForkOptionsBuilder(execFactory).javaForkOptions(javaForkOptions)
    }
}
