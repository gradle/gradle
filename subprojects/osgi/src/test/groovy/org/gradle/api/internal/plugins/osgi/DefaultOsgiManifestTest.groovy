/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.plugins.osgi

import aQute.bnd.osgi.Analyzer
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.java.archives.Attributes
import org.gradle.api.java.archives.internal.DefaultAttributes
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.internal.Factory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.jar.Manifest

class DefaultOsgiManifestTest extends Specification {
    private static final String ARBITRARY_SECTION = "A-Different-Section"
    private static final String ARBITRARY_ATTRIBUTE = "Silly-Attribute"
    private static final String ANOTHER_ARBITRARY_ATTRIBUTE = "Serious-Attribute"

    DefaultOsgiManifest osgiManifest

    Factory<ContainedVersionAnalyzer> analyzerFactoryMock = Mock(Factory)
    ContainedVersionAnalyzer analyzerMock = Mock(ContainedVersionAnalyzer)

    FileResolver fileResolver = Mock(FileResolver)

    @Shared specialFields = [
            ["description", Analyzer.BUNDLE_DESCRIPTION],
            ["docURL", Analyzer.BUNDLE_DOCURL],
            ["license", Analyzer.BUNDLE_LICENSE],
            ["name", Analyzer.BUNDLE_NAME],
            ["symbolicName", Analyzer.BUNDLE_SYMBOLICNAME],
            ["vendor", Analyzer.BUNDLE_VENDOR],
            ["version", Analyzer.BUNDLE_VERSION]
    ]

    def setup() {
        osgiManifest = new DefaultOsgiManifest(fileResolver)
        interaction {
            _ * analyzerFactoryMock.create() >> analyzerMock
        }
        osgiManifest.analyzerFactory = analyzerFactoryMock
    }

    def initialState() {
        expect:
        osgiManifest.instructions.isEmpty()
        osgiManifest.analyzerFactory != null
    }

    @Unroll
    "set then get - #field"() {
        given:
        def testValue = "testValue"

        when:
        osgiManifest."$field" = testValue

        then:
        osgiManifest."$field" == testValue

        where:
        field << specialFields.collect { it[0] }
    }

    @Unroll
    "can mix and match properties and instructions - #field"(String field, String name) {
        given:
        def testValue = "testValue"

        when:
        osgiManifest.instruction(name, testValue)

        then:
        osgiManifest."$field" == testValue
        osgiManifest.instructionValue(name) == [testValue]

        when:
        testValue = "changed"

        and:
        osgiManifest."$field" = testValue

        then:
        osgiManifest."$field" == testValue
        osgiManifest.instructionValue(name) == [testValue]

        when:
        osgiManifest.instruction(name, "other")

        then:
        osgiManifest."$field" == "$testValue,other"

        where:
        [field, name] << specialFields
    }

    @Unroll
    "can set modelled properties with instruction - #name"(String field, String name) {
        given:
        setUpOsgiManifest()
        def testValue = "testValue"

        when:
        osgiManifest.instructionReplace(name, testValue)

        and:
        prepareMock()

        then:
        def effectiveManifest = osgiManifest.getEffectiveManifest()
        effectiveManifest.attributes[name] == testValue

        where:
        [field, name] << specialFields
    }

    private DefaultOsgiManifest createManifest() {
        return new DefaultOsgiManifest(fileResolver)
    }

    def addInstruction() {
        given:
        String testInstructionName = "someInstruction"
        String instructionValue1 = "value1"
        String instructionValue2 = "value2"
        String instructionValue3 = "value3"

        expect:
        osgiManifest.is osgiManifest.instruction(testInstructionName, instructionValue1, instructionValue2)
        osgiManifest.instructions[testInstructionName] == [instructionValue1, instructionValue2]

        when:
        osgiManifest.instruction(testInstructionName, instructionValue3)

        then:
        osgiManifest.instructions[testInstructionName] == [instructionValue1, instructionValue2, instructionValue3]
    }

    def addInstructionFirst() {
        given:
        String testInstructionName = "someInstruction"
        String instructionValue1 = "value1"
        String instructionValue2 = "value2"
        String instructionValue3 = "value3"

        expect:
        osgiManifest.is osgiManifest.instructionFirst(testInstructionName, instructionValue1, instructionValue2)
        osgiManifest.instructions[testInstructionName] == [instructionValue1, instructionValue2]

        when:
        osgiManifest.instructionFirst(testInstructionName, instructionValue3)

        then:
        osgiManifest.instructions[testInstructionName] == [instructionValue3, instructionValue1, instructionValue2]
    }

    def instructionValue() {
        given:
        String testInstructionName = "someInstruction"
        String instructionValue1 = "value1"
        String instructionValue2 = "value2"

        when:
        osgiManifest.instruction(testInstructionName, instructionValue1, instructionValue2)

        then:
        osgiManifest.instructionValue(testInstructionName) == [instructionValue1, instructionValue2]
    }

    def getEffectiveManifest() {
        given:
        setUpOsgiManifest()
        prepareMock()

        when:
        DefaultManifest manifest = osgiManifest.getEffectiveManifest()
        manifest.attributes.remove(Analyzer.BND_LASTMODIFIED) // this is generated based on when the test runs
        DefaultManifest defaultManifest = getDefaultManifestWithOsgiValues()
        DefaultManifest expectedManifest = new DefaultManifest(fileResolver).attributes(defaultManifest.getAttributes())
        for (Map.Entry<String, Attributes> ent: defaultManifest.getSections().entrySet()) {
            expectedManifest.attributes(ent.getValue(), ent.getKey())
        }

        then:
        manifest.attributes == expectedManifest.attributes
        manifest.sections == expectedManifest.sections
    }

    def merge() {
        given:
        setUpOsgiManifest()
        prepareMock()

        when:
        DefaultManifest otherManifest = new DefaultManifest(fileResolver)
        otherManifest.mainAttributes(somekey: "somevalue")
        otherManifest.mainAttributes((Analyzer.BUNDLE_VENDOR): "mergeVendor")
        osgiManifest.from(otherManifest)
        DefaultManifest defaultManifest = getDefaultManifestWithOsgiValues()
        DefaultManifest expectedManifest = new DefaultManifest(fileResolver).attributes(defaultManifest.getAttributes())
        for (Map.Entry<String, Attributes> ent: defaultManifest.getSections().entrySet()) {
            expectedManifest.attributes(ent.getValue(), ent.getKey())
        }
        expectedManifest.attributes(otherManifest.getAttributes())

        DefaultManifest manifest = osgiManifest.getEffectiveManifest()
        manifest.attributes.remove(Analyzer.BND_LASTMODIFIED) // this is generated based on when the test runs

        then:
        manifest.isEqualsTo expectedManifest
    }

    def generateWithNull() {
        given:
        setUpOsgiManifest()
        prepareMockForNullTest()

        when:
        osgiManifest.setVersion(null)

        then:
        osgiManifest.effectiveManifest
    }

    private setUpOsgiManifest() {
        def fileCollection = Mock(FileCollection)
        interaction {
            _ * fileCollection.files >> ([new File("someFile")] as Set)
        }

        osgiManifest.setSymbolicName("symbolic")
        osgiManifest.setName("myName")
        osgiManifest.setVersion("myVersion")
        osgiManifest.setDescription("myDescription")
        osgiManifest.setLicense("myLicense")
        osgiManifest.setVendor("myVendor")
        osgiManifest.setDocURL("myDocUrl")
        osgiManifest.instruction(Analyzer.EXPORT_PACKAGE, "pack1", "pack2")
        osgiManifest.instruction(Analyzer.IMPORT_PACKAGE, "pack3", "pack4")
        osgiManifest.setClasspath(fileCollection)
        osgiManifest.setClassesDir(new File("someDir"))
        addPlainAttributesAndSections(osgiManifest)
    }

    private prepareMock() {
        interaction {
            1 * analyzerMock.setProperty(Analyzer.BUNDLE_VERSION, osgiManifest.version)
        }
        prepareMockForNullTest()
    }

    private prepareMockForNullTest() {
        interaction {
            1 * analyzerMock.setProperty(Analyzer.BUNDLE_SYMBOLICNAME, osgiManifest.symbolicName)
            1 * analyzerMock.setProperty(Analyzer.BUNDLE_NAME, osgiManifest.name)
            1 * analyzerMock.setProperty(Analyzer.BUNDLE_DESCRIPTION, osgiManifest.description)
            1 * analyzerMock.setProperty(Analyzer.BUNDLE_LICENSE, osgiManifest.license)
            1 * analyzerMock.setProperty(Analyzer.BUNDLE_VENDOR, osgiManifest.vendor)
            1 * analyzerMock.setProperty(Analyzer.BUNDLE_DOCURL, osgiManifest.docURL)
            1 * analyzerMock.setProperty(Analyzer.EXPORT_PACKAGE, osgiManifest.instructionValue(Analyzer.EXPORT_PACKAGE).join(","))
            1 * analyzerMock.setProperty(Analyzer.IMPORT_PACKAGE, osgiManifest.instructionValue(Analyzer.IMPORT_PACKAGE).join(","))

            1 * analyzerMock.setProperty(ARBITRARY_ATTRIBUTE, "I like green eggs and ham.")

            1 * analyzerMock.setJar(osgiManifest.classesDir)
            1 * analyzerMock.setClasspath(osgiManifest.classpath.files.toArray())

            Manifest testManifest = new Manifest()
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_SYMBOLICNAME, osgiManifest.getSymbolicName())
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_NAME, osgiManifest.getName())
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_DESCRIPTION, osgiManifest.getDescription())
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_LICENSE, osgiManifest.getLicense())
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_VENDOR, osgiManifest.getVendor())
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_DOCURL, osgiManifest.getDocURL())
            testManifest.getMainAttributes().putValue(Analyzer.BUNDLE_VERSION, osgiManifest.getVersion())
            testManifest.getMainAttributes().putValue(Analyzer.EXPORT_PACKAGE, osgiManifest.instructionValue(Analyzer.EXPORT_PACKAGE).join(","))
            testManifest.getMainAttributes().putValue(Analyzer.IMPORT_PACKAGE, osgiManifest.instructionValue(Analyzer.IMPORT_PACKAGE).join(","))

            _ * analyzerMock.calcManifest() >> testManifest

        }
    }

    private DefaultManifest getDefaultManifestWithOsgiValues() {
        DefaultManifest manifest = new DefaultManifest(fileResolver)
        manifest.getAttributes().put(Analyzer.BUNDLE_SYMBOLICNAME, osgiManifest.getSymbolicName())
        manifest.getAttributes().put(Analyzer.BUNDLE_NAME, osgiManifest.getName())
        manifest.getAttributes().put(Analyzer.BUNDLE_DESCRIPTION, osgiManifest.getDescription())
        manifest.getAttributes().put(Analyzer.BUNDLE_LICENSE, osgiManifest.getLicense())
        manifest.getAttributes().put(Analyzer.BUNDLE_VENDOR, osgiManifest.getVendor())
        manifest.getAttributes().put(Analyzer.BUNDLE_VERSION, osgiManifest.getVersion())
        manifest.getAttributes().put(Analyzer.BUNDLE_DOCURL, osgiManifest.getDocURL())
        manifest.getAttributes().put(Analyzer.EXPORT_PACKAGE, osgiManifest.instructionValue(Analyzer.EXPORT_PACKAGE).join(","))
        manifest.getAttributes().put(Analyzer.IMPORT_PACKAGE, osgiManifest.instructionValue(Analyzer.IMPORT_PACKAGE).join(","))
        addPlainAttributesAndSections(manifest)
        return manifest
    }

    private void addPlainAttributesAndSections(DefaultManifest manifest) {
        manifest.getAttributes().put(ARBITRARY_ATTRIBUTE, "I like green eggs and ham.")
        Attributes sectionAtts = new DefaultAttributes()
        sectionAtts.put(ANOTHER_ARBITRARY_ATTRIBUTE, "Death is the great equalizer.")
        manifest.getSections().put(ARBITRARY_SECTION, sectionAtts)
    }
}
