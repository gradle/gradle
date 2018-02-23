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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.compile.ForkOptions
import org.gradle.internal.jvm.Jvm
import spock.lang.Specification


class ForkOptionsConverterTest extends Specification {
    ForkOptionsConverter converter = new ForkOptionsConverter(Mock(FileResolver))

    def "can convert a fork options to a java fork options"() {
        ForkOptions forkOptions = new ForkOptions()
        forkOptions.memoryInitialSize = "128m"
        forkOptions.memoryMaximumSize = "1g"
        forkOptions.jvmArgs = ["-foo", "-bar"]
        forkOptions.executable = "/foo/bar"

        when:
        def javaForkOptions = converter.transform(forkOptions)

        then:
        javaForkOptions.minHeapSize == "128m"
        javaForkOptions.maxHeapSize == "1g"
        javaForkOptions.jvmArgs == ["-foo", "-bar"]
        javaForkOptions.executable == "/foo/bar"
    }

    def "can convert a partially configured base fork options"() {
        ForkOptions forkOptions = new ForkOptions()
        forkOptions.executable = "/foo/bar"
        forkOptions.memoryInitialSize = "128m"

        when:
        def javaForkOptions = converter.transform(forkOptions)

        then:
        javaForkOptions.minHeapSize == "128m"
        javaForkOptions.maxHeapSize == null
        javaForkOptions.jvmArgs == []
        javaForkOptions.executable == "/foo/bar"
    }

    def "can convert empty fork options"() {
        ForkOptions forkOptions = new ForkOptions()

        when:
        def javaForkOptions = converter.transform(forkOptions)

        then:
        javaForkOptions.executable == Jvm.current().javaExecutable.toString()
        javaForkOptions.minHeapSize == null
        javaForkOptions.maxHeapSize == null
        javaForkOptions.jvmArgs == []
    }
}
