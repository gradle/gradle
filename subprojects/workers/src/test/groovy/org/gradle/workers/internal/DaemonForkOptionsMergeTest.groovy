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
import org.gradle.process.internal.DefaultJavaForkOptions
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class DaemonForkOptionsMergeTest extends Specification {
    JavaForkOptions forkOptions = javaForkOptions {
        workingDir = systemSpecificAbsolutePath("foo")
    }
    DaemonForkOptions options1 = new DaemonForkOptionsBuilder(TestFiles.execFactory())
        .javaForkOptions(forkOptions)
        .classpath([new File("lib/lib1.jar"), new File("lib/lib2.jar")])
        .sharedPackages(["foo.bar", "baz.bar"])
        .keepAliveMode(KeepAliveMode.SESSION)
        .build()
    DaemonForkOptions options2 = new DaemonForkOptionsBuilder(TestFiles.execFactory())
        .javaForkOptions(forkOptions)
        .classpath([new File("lib/lib2.jar"), new File("lib/lib3.jar")])
        .sharedPackages(["baz.bar", "other"])
        .keepAliveMode(KeepAliveMode.SESSION)
        .build()
    DaemonForkOptions merged = options1.mergeWith(options2)

    def "concatenates classpath (retaining order, eliminating duplicates)"() {
        expect:
        merged.classpath as List == [new File("lib/lib1.jar"), new File("lib/lib2.jar"), new File("lib/lib3.jar")]
    }

    def "concatenates sharedPackages (retaining order, eliminating duplicates)"() {
        expect:
        merged.sharedPackages as List == ["foo.bar", "baz.bar", "other"]
    }

    def "can merge with different java fork options"() {
        forkOptions = javaForkOptions {
            workingDir = systemSpecificAbsolutePath("foo")
            minHeapSize = "256m"
            maxHeapSize = "1024m"
            jvmArgs = ["-server", "-verbose:gc"]
        }
        def forkOptions2 = javaForkOptions {
            workingDir = systemSpecificAbsolutePath("foo")
            minHeapSize = "128m"
            maxHeapSize = "2g"
            debug = true
            jvmArgs = ["-Xverify:none", "-server"]
        }
        options1 = new DaemonForkOptionsBuilder(TestFiles.execFactory())
            .javaForkOptions(forkOptions)
            .classpath([new File("lib/lib1.jar"), new File("lib/lib2.jar")])
            .sharedPackages(["foo.bar", "baz.bar"])
            .build()
        options2 = new DaemonForkOptionsBuilder(TestFiles.execFactory())
            .javaForkOptions(forkOptions2)
            .classpath([new File("lib/lib2.jar"), new File("lib/lib3.jar")])
            .sharedPackages(["baz.bar", "other"])
            .build()
        merged = options1.mergeWith(options2)

        expect:
        merged.javaForkOptions.minHeapSize == "256m"
        merged.javaForkOptions.maxHeapSize == "2048m"
        merged.javaForkOptions.jvmArgs == ["-server", "-verbose:gc", "-Xverify:none"]
        merged.javaForkOptions.debug
    }

    def "throws an exception when merging options with different keepAlive modes"() {
        options2 = new DaemonForkOptionsBuilder(TestFiles.execFactory())
            .javaForkOptions(forkOptions)
            .classpath([new File("lib/lib2.jar"), new File("lib/lib3.jar")])
            .sharedPackages(["baz.bar", "other"])
            .keepAliveMode(KeepAliveMode.DAEMON)
            .build()

        when:
        options1.mergeWith(options2)

        then:
        thrown(IllegalArgumentException)
    }

    JavaForkOptions javaForkOptions(Closure closure) {
        JavaForkOptions options = new DefaultJavaForkOptions(TestFiles.pathToFileResolver())
        options.with(closure)
        return options
    }
}
