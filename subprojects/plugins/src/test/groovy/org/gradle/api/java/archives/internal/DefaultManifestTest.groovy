/*
 * Copyright 2007 the original author or authors.
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

import org.apache.tools.ant.taskdefs.Manifest
import org.apache.tools.ant.taskdefs.Manifest.Attribute
import org.apache.tools.ant.taskdefs.Manifest.Section
import org.gradle.api.internal.file.FileResolver
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */

class DefaultManifestTest extends Specification {
    def static final MANIFEST_VERSION_MAP = ['Manifest-Version': '1.0']
    def fileResolver = Mock(FileResolver)
    DefaultManifest gradleManifest = new DefaultManifest(fileResolver)

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def testInitWithFileReolver() {
        expect:
        gradleManifest.attributes == MANIFEST_VERSION_MAP
        !gradleManifest.sections
        !gradleManifest.mergeSpecs
    }

    def testInitWithPathFileResolver() {
        TestFile manifestFile = tmpDir.file('somefile')
        def fileMap = [Key2: 'value2File', key4: 'value4File', key6: 'value6File']
        def fileSectionMap = [Keysec2: 'value2Secfile', keysec4: 'value5Secfile', keysec6: 'value6Secfile']
        String content = ''
        fileMap.each {key, value ->
            content += String.format("%s: %s%n", key, value)
        }
        content += String.format("Name: %s%n", 'section')
        fileSectionMap.each {key, value ->
            content += String.format("%s: %s%n", key, value)
        }
        manifestFile.write(content)
        fileResolver.resolve('file') >> manifestFile

        when:
        DefaultManifest manifest = new DefaultManifest('file', fileResolver)

        then:
        manifest.getAttributes() == fileMap + MANIFEST_VERSION_MAP
        manifest.sections.section == fileSectionMap
    }

    def testAddMainAttributes() {
        Map attributes = [key1: 'value1', key2: 'value2']
        Map attributes2 = [key3: 'value3']

        when:
        def expectToReturnSelf = gradleManifest.mainAttributes(attributes)
        gradleManifest.attributes(attributes2)

        then:
        gradleManifest.is(expectToReturnSelf)
        gradleManifest.getAttributes() == attributes + attributes2 + MANIFEST_VERSION_MAP
    }

    def testAddSectionAttributes() {
        String section1 = 'section1'
        String section2 = 'section2'
        Map attributes = [key1: 'value1', key2: 'value2']
        Map attributes2 = [key3: 'value3']
        Map attributes3 = [key3: 'value3']

        when:
        def expectToReturnSelf = gradleManifest.attributes(attributes, section1)
        gradleManifest.attributes(attributes2, section1)
        gradleManifest.attributes(attributes3, section2)

        then:
        gradleManifest.is(expectToReturnSelf)
        gradleManifest.sections.section1 == attributes + attributes2
        gradleManifest.sections.section2 == attributes3
        gradleManifest.getSections() == [section1: attributes + attributes2, section2: attributes3]
    }

    def clear() {
        gradleManifest.attributes(key1: 'value1')
        gradleManifest.attributes('section', key1: 'value1')
        gradleManifest.from(new DefaultManifest(Mock(FileResolver)))

        when:
        gradleManifest.clear()

        then:
        gradleManifest.getAttributes() == MANIFEST_VERSION_MAP
        !gradleManifest.sections
        !gradleManifest.mergeSpecs
    }

    def merge() {
        gradleManifest.attributes(key1: 'value1')
        gradleManifest.from(new DefaultManifest(fileResolver).attributes(key2: 'value2', key3: 'value3')) {
            eachEntry { details ->
                if (details.key == 'key3') {
                    details.exclude()
                }
            }
        }
        gradleManifest.from(new DefaultManifest(fileResolver).attributes(key4: 'value4'))

        expect:
        gradleManifest.effectiveManifest.getAttributes() == [key1: 'value1', key2: 'value2', key4: 'value4'] + MANIFEST_VERSION_MAP
    }

    def write() {
        Map testMainAttributes = [key1: 'value1', key2: 'value2', key3: 'value3', key4: 'value4'] as LinkedHashMap
        Map testSectionAttributes = [sectionkey1: 'sectionvalue1']
        String testSection = 'section'

        DefaultManifest gradleManifest = new DefaultManifest(fileResolver)
        gradleManifest.from(new DefaultManifest(fileResolver).attributes(testMainAttributes))
        gradleManifest.attributes(testSectionAttributes, testSection)

        Manifest expectedManifest = new Manifest()
        expectedManifest.addConfiguredAttribute(new Attribute('key1', 'value1'))
        expectedManifest.addConfiguredAttribute(new Attribute('key2', 'value2'))
        expectedManifest.addConfiguredAttribute(new Attribute('key3', 'value3'))
        expectedManifest.addConfiguredAttribute(new Attribute('key4', 'value4'))
        expectedManifest.addConfiguredAttribute(new Attribute('Manifest-Version', '1.0'))
        Section section = new Section()
        section.setName testSection
        section.addConfiguredAttribute(new Attribute('sectionkey1', 'sectionvalue1'))
        expectedManifest.addConfiguredSection(section)
        def StringWriter stringWriter = new StringWriter()

        when:
        gradleManifest.writeTo(stringWriter)
        Manifest actualManifest = new Manifest(new StringReader(stringWriter.toString()))
        def actualOrderedKeys = []
        actualManifest.getMainSection().getAttributeKeys().each { element ->
            actualOrderedKeys.add(element)
        }

        then:
        actualManifest == expectedManifest
        actualOrderedKeys == testMainAttributes.keySet() as List
    }

    def writeWithPath() {
        TestFile manifestFile = tmpDir.file('someNonexistingDir').file('someFile')
        DefaultManifest manifest = new DefaultManifest(fileResolver).attributes(key1: 'value1')
        fileResolver.resolve('file') >> manifestFile
        
        when:
        manifest.writeTo('file')
        Manifest fileManifest = new Manifest(new FileReader(manifestFile))
        Manifest expectedManifest = new Manifest()
        expectedManifest.addConfiguredAttribute(new Attribute('key1', 'value1'))
        expectedManifest.addConfiguredAttribute(new Attribute('Manifest-Version', '1.0'))

        then:
        fileManifest.equals(expectedManifest)
    }
}