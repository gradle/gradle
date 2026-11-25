/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.process.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.nio.charset.Charset

class AttachedJvmOptionsTest extends Specification {
    def objects = TestUtil.objectFactory()
    def jvmArgs = objects.listProperty(String)
    def systemProperties = objects.mapProperty(String, Object)
    def minHeapSize = objects.property(String)
    def maxHeapSize = objects.property(String)
    def enableAssertions = objects.property(Boolean)
    def encoding = objects.property(String)
    def bootstrapClasspath = objects.fileCollection()
    def options = new JvmOptions(TestFiles.fileCollectionFactory())

    def setup() {
        options.setEnableAssertions(true)
        options.attach(jvmArgs, systemProperties, minHeapSize, maxHeapSize, enableAssertions, encoding, bootstrapClasspath)
    }

    def "current values become conventions"() {
        expect:
        enableAssertions.get()
        encoding.get() == Charset.defaultCharset().name()
        jvmArgs.get() == []
        options.getEnableAssertions()
    }

    def "arguments are parsed when set and stored in the lazy properties"() {
        when:
        options.jvmArgs("-Xmx1g", "-Xms64m", "-Dk=v", "-da", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-16")

        then:
        maxHeapSize.get() == "1g"
        minHeapSize.get() == "64m"
        systemProperties.get() == [k: "v"]
        !enableAssertions.get()
        encoding.get() == "UTF-16"
        jvmArgs.get() == ["-XX:+UseSerialGC"]
        options.getJvmArgs() == ["-XX:+UseSerialGC"]
        options.allJvmArgs.containsAll(["-Dk=v", "-XX:+UseSerialGC", "-Xms64m", "-Xmx1g", "-Dfile.encoding=UTF-16"])
    }

    def "a system property with a null value is stored as empty"() {
        when:
        options.systemProperty("k", null)

        then:
        systemProperties.get() == [k: ""]
        options.allJvmArgs.contains("-Dk")
    }

    def "arguments bound lazily are parsed by the copy and win over the dedicated values"() {
        options.setMaxHeapSize("256m")
        jvmArgs.add(TestUtil.providerFactory().provider { "-Xmx96m" })

        when:
        def copy = options.createCopy(TestFiles.fileCollectionFactory())

        then:
        copy.maxHeapSize == "96m"
        copy.jvmArgs == []
    }

    def "options that are not attached keep their fields"() {
        def plain = new JvmOptions(TestFiles.fileCollectionFactory())

        when:
        plain.jvmArgs("-Xmx1g")

        then:
        plain.maxHeapSize == "1g"
        !maxHeapSize.present
    }
}
