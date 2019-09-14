/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.java.archives.internal

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.provider.Provider
import org.gradle.util.TextUtil
import spock.lang.Specification

class ManifestPrinterTest extends Specification {
    def fileResolver = Mock(FileResolver)
    DefaultManifest manifest = new DefaultManifest(fileResolver)

    private def print(DefaultManifest manifest) {
        StringWriter sw = new StringWriter()
        ManifestPrinter mp = new ManifestPrinter(sw)
        mp.print(manifest)
        mp.flush()
        return sw.toString()
    }

    def manifestLooksOk() {
        when:
        manifest.attributes('Manifest-Version': '1.0', 'Title': 'test',
        'LongKey1-0123456789012345678901234567890123456789012345678901234567890': 'ok',
        'LongKey2-0123456789012345678901234567890123456789012345678901234567890': '😃😃😃😃😃😃',
        'LongValue': '😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃')
        manifest.attributes(['SectionTitle': 'hello', 'Manifest-Version': '1.0'], "Section1")
        manifest.attributes(['SectionTitle': '丈丈丈丈', 'Manifest-Version': 'निनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनि'], "Section2")

        then:
        // :\u0020 is used to represent space and avoid IDEs to truncate "trailing whitespace"
        // The key is 70 characters long, and it is followed by ": ". That is why the space is required
        print(manifest) ==
            TextUtil.convertLineSeparators(
                '''|Manifest-Version: 1.0
                   |Title: test
                   |LongKey1-0123456789012345678901234567890123456789012345678901234567890:\u0020
                   | ok
                   |LongKey2-0123456789012345678901234567890123456789012345678901234567890:\u0020
                   | 😃😃😃😃😃😃
                   |LongValue: 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   | 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   | 😃
                   |
                   |Name: Section1
                   |SectionTitle: hello
                   |Manifest-Version: 1.0
                   |
                   |Name: Section2
                   |SectionTitle: 丈丈丈丈
                   |Manifest-Version: निनिनिनिनिनिनिनिनि
                   | निनिनिनिनिनिनिनिनिनिनिन
                   | िनिनिनिनिनिनिनिनिनि
                   |
                   |'''.stripMargin(), "\r\n")
    }

    def "provider in values"() {
        when:
        def manifestVersion = Stub(Provider)
        manifestVersion.get() >> '1.0'
        def hello = Stub(Provider)
        hello.get() >> 'world'
        manifest.attributes('Manifest-Version': manifestVersion, 'Hello': hello)
        def section = Stub(Provider)
        section.get() >> 'section title'
        manifest.attributes(['SectionTitle': section], "Section1")

        then:
        print(manifest) ==
            TextUtil.convertLineSeparators(
                '''|Manifest-Version: 1.0
                   |Hello: world
                   |
                   |Name: Section1
                   |SectionTitle: section title
                   |
                   |'''.stripMargin(), "\r\n")
    }

    def "normalizes manifest keys on write"() {
        when:
        // Manifests are case-insensitive, however the specification suggests to use the canonical case
        // For now only Manifest-Version is normalized
        manifest.attributes(
            'manifest-version': '1.0',
            'class-path': 'test.jar',
            'implementation-title': 'test',
            'implementation-version': '42.0',
            'implementation-vendor': 'example',
            'specification-title': 'test',
            'specification-version': '42.0',
            'specification-vendor': 'example',
            'sealed': 'false'
        )

        then:
        print(manifest) ==
            TextUtil.convertLineSeparators(
                '''|Manifest-Version: 1.0
                   |class-path: test.jar
                   |implementation-title: test
                   |implementation-version: 42.0
                   |implementation-vendor: example
                   |specification-title: test
                   |specification-version: 42.0
                   |specification-vendor: example
                   |sealed: false
                   |
                   |'''.stripMargin(), "\r\n")
    }
}
