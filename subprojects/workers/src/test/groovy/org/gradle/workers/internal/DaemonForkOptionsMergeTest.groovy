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

import spock.lang.Specification

class DaemonForkOptionsMergeTest extends Specification {
    DaemonForkOptions options1 = new DaemonForkOptions("200m", "1g", [" -Dfork=true ", "-Xdebug=false"],
            [new File("lib/lib1.jar"), new File("lib/lib2.jar")], ["foo.bar", "baz.bar"])
    DaemonForkOptions options2 = new DaemonForkOptions("1g", "2000m", ["-XX:MaxHeapSize=300m", "-Dfork=true"],
            [new File("lib/lib2.jar"), new File("lib/lib3.jar")], ["baz.bar", "other"])
    DaemonForkOptions merged = options1.mergeWith(options2)

    def "takes highest minHeapSize"() {
        expect:
        merged.minHeapSize == "1024m"
    }

    def "takes highest maxHeapSize"() {
        expect:
        merged.maxHeapSize == "2000m"
    }

    def "concatenates jvmArgs (retaining order, eliminating duplicates)"() {
        expect:
        merged.jvmArgs as List == ["-Dfork=true", "-Xdebug=false", "-XX:MaxHeapSize=300m"]
    }

    def "concatenates classpath (retaining order, eliminating duplicates)"() {
        expect:
        merged.classpath as List == [new File("lib/lib1.jar"), new File("lib/lib2.jar"), new File("lib/lib3.jar")]
    }

    def "concatenates sharedPackages (retaining order, eliminating duplicates)"() {
        expect:
        merged.sharedPackages as List == ["foo.bar", "baz.bar", "other"]
    }
}
