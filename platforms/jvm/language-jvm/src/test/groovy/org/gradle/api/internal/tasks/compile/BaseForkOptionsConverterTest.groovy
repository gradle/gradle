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


import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.compile.BaseForkOptions
import spock.lang.Specification

class BaseForkOptionsConverterTest extends Specification {
    BaseForkOptionsConverter converter = new BaseForkOptionsConverter(TestFiles.execFactory())

    def "converts a base fork options to a java fork options"() {
        BaseForkOptions baseForkOptions = new BaseForkOptions()
        baseForkOptions.memoryInitialSize = "128m"
        baseForkOptions.memoryMaximumSize = "1g"
        baseForkOptions.jvmArgs = ["-foo", "-bar"]

        when:
        def javaForkOptions = converter.transform(baseForkOptions)

        then:
        javaForkOptions.minHeapSize == "128m"
        javaForkOptions.maxHeapSize == "1g"
        javaForkOptions.jvmArgs == ["-foo", "-bar"]
    }

    def "can convert a partially configured base fork options"() {
        BaseForkOptions baseForkOptions = new BaseForkOptions()
        baseForkOptions.memoryInitialSize = "128m"

        when:
        def javaForkOptions = converter.transform(baseForkOptions)

        then:
        javaForkOptions.minHeapSize == "128m"
        javaForkOptions.maxHeapSize == null
        javaForkOptions.jvmArgs == []
    }
}
