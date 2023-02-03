/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.platform.base.internal

import org.gradle.api.Named
import spock.lang.Specification

class DefaultBinaryNamingSchemeTest extends Specification {
    def "generates task names from type and dimension attributes"() {
        expect:
        def namingScheme = createNamingScheme(parentName, type, dimensions)
        namingScheme.getTaskName(verb, target) == taskName

        where:
        parentName | type   | dimensions     | verb       | target    | taskName
        null       | "type" | []             | null       | null      | "type"
        null       | "type" | ["one", "two"] | null       | null      | "oneTwoType"
        null       | "type" | ["one", "two"] | "send"     | null      | "sendOneTwoType"
        "test"     | null   | []             | null       | null      | "test"
        "test"     | "type" | []             | null       | null      | "testType"
        "test"     | "type" | []             | null       | "classes" | "testTypeClasses"
        "test"     | null   | []             | null       | "classes" | "testClasses"
        "test"     | "type" | []             | "assemble" | null      | "assembleTestType"
        "test"     | "type" | []             | "compile"  | "java"    | "compileTestTypeJava"
        "test"     | "type" | ["one", "two"] | null       | null      | "testOneTwoType"
        "test"     | "type" | ["one", "two"] | null       | "classes" | "testOneTwoTypeClasses"
        "test"     | "type" | ["one", "two"] | "assemble" | null      | "assembleTestOneTwoType"
        "test"     | "type" | ["one", "two"] | "compile"  | "java"    | "compileTestOneTwoTypeJava"
    }

    def "generates task names from binary name attribute"() {
        expect:
        def namingScheme = DefaultBinaryNamingScheme.component(parentName).withBinaryName(binaryName)
        namingScheme.getTaskName(verb, target) == taskName

        where:
        parentName | binaryName | verb      | target | taskName
        null       | "binary"   | null      | null   | "binary"
        null       | "binary"   | "compile" | null   | "compileBinary"
        null       | "binary"   | "compile" | "java" | "compileBinaryJava"
        "test"     | "binary"   | null      | null   | "testBinary"
        "test"     | "binary"   | "compile" | null   | "compileTestBinary"
        "test"     | "binary"   | "compile" | "java" | "compileTestBinaryJava"
    }

    def "generates binary name from type and dimension attributes"() {
        def namingScheme = createNamingScheme("parent", "executable", dimensions)

        expect:
        namingScheme.binaryName == binaryName

        where:
        dimensions                                 | binaryName
        []                                         | "executable"
        ["one", "two"]                             | "oneTwoExecutable"
        ["enterpriseEdition", "osx_x64", "static"] | "enterpriseEditionOsx_x64StaticExecutable"
        ["EnterpriseEdition", "Osx_x64", "Static"] | "enterpriseEditionOsx_x64StaticExecutable"
    }

    def "generates output directory from type and dimension attributes"() {
        def namingScheme = createNamingScheme(parentName, binaryType, dimensions)

        expect:
        namingScheme.getOutputDirectory(new File("."), outputType) == new File(".", outputDir)

        where:
        parentName    | binaryType   | dimensions                                 | outputType | outputDir
        "test"        | null         | []                                         | null       | "test"
        "test"        | null         | ["one", "two"]                             | null       | "test/one/two"
        "test"        | null         | []                                         | "jars"     | "jars/test"
        "test"        | "executable" | []                                         | null       | "test/executable"
        "test"        | "executable" | ["one", "two"]                             | null       | "test/executable/one/two"
        "mainLibrary" | "executable" | ["enterpriseEdition", "osx_x64", "static"] | null       | "mainLibrary/executable/enterpriseEdition/osx_x64/static"
        "mainLibrary" | "executable" | ["EnterpriseEdition", "Osx_x64", "Static"] | null       | "mainLibrary/executable/enterpriseEdition/osx_x64/static"
        "mainLibrary" | "executable" | ["EnterpriseEdition", "Osx_x64", "Static"] | "exes"     | "exes/mainLibrary/executable/enterpriseEdition/osx_x64/static"
    }

    def "generates output directory from binary name"() {
        def namingScheme = createNamingScheme(parentName, null, []).withBinaryName(binaryName)

        expect:
        namingScheme.getOutputDirectory(new File("."), outputType) == new File(".", outputDir)

        where:
        parentName  | binaryName   | outputType     | outputDir
        null        | "LinuxTest"  | null           | "linuxTest"
        null        | "linux-test" | null           | "linux-test"
        null        | "LinuxTest"  | "test-results" | "test-results/linuxTest"
        "TestSuite" | "LinuxTest"  | null           | "testSuite/linuxTest"
        "TestSuite" | "LinuxTest"  | "bin-files"    | "bin-files/testSuite/linuxTest"
    }

    def "prefers role over binary type in output directory names"() {
        def namingScheme = DefaultBinaryNamingScheme.component("testSuite").withBinaryType("GoogleTestExecutable").withVariantDimension("linux")

        expect:
        namingScheme.getOutputDirectory(new File(".")) == new File(".", "testSuite/googleTestExecutable/linux")
        namingScheme.withRole("executable", false).getOutputDirectory(new File(".")) == new File(".", "testSuite/executable/linux")
        namingScheme.withRole("executable", true).getOutputDirectory(new File(".")) == new File(".", "testSuite/linux")
    }

    def "generates description from type and dimension attributes"() {
        def namingScheme = createNamingScheme(parentName, typeName, dimensions)

        expect:
        namingScheme.getDescription() == lifecycleName

        where:
        parentName | typeName        | dimensions     | lifecycleName
        null       | "Executable"    | []             | "executable 'executable'"
        "parent"   | "Executable"    | []             | "executable 'parent:executable'"
        null       | "SharedLibrary" | []             | "shared library 'sharedLibrary'"
        "parent"   | "SharedLibrary" | []             | "shared library 'parent:sharedLibrary'"
        "parent"   | "SharedLibrary" | ["one"]        | "shared library 'parent:one:sharedLibrary'"
        "parent"   | "SharedLibrary" | ["one", "two"] | "shared library 'parent:one:two:sharedLibrary'"
    }

    def "generates description from type and binary name attributes"() {
        def namingScheme = createNamingScheme(parentName, typeName, []).withBinaryName(binaryName)

        expect:
        namingScheme.getDescription() == lifecycleName

        where:
        parentName | typeName        | binaryName  | lifecycleName
        null       | "Executable"    | "LinuxTest" | "executable 'linuxTest'"
        "parent"   | "Executable"    | "LinuxTest" | "executable 'parent:linuxTest'"
        null       | "SharedLibrary" | "SomeLib64" | "shared library 'someLib64'"
        "parent"   | "SharedLibrary" | "someLib64" | "shared library 'parent:someLib64'"
    }

    def "prefers declared binary name over default binary name"() {
        def namingScheme = DefaultBinaryNamingScheme.component("testSuite")
                .withBinaryType("GoogleTestExecutable")
                .withVariantDimension("linux")
                .withBinaryName("LinuxTest")

        expect:
        namingScheme.binaryName == "LinuxTest"
        namingScheme.description == "google test executable 'testSuite:linuxTest'"
        namingScheme.getTaskName("compile") == "compileTestSuiteLinuxTest"
        namingScheme.getOutputDirectory(new File(".")) == new File(".", "testSuite/linuxTest")
    }

    def "can create copies"() {
        def original = createNamingScheme("parent", "type", ["dim1"])

        expect:
        original.getOutputDirectory(new File(".")) == new File(".", "parent/type/dim1")
        original.withComponentName("other").getOutputDirectory(new File(".")) == new File(".", "other/type/dim1")
        original.withBinaryType("other").getOutputDirectory(new File(".")) == new File(".", "parent/other/dim1")
        original.withVariantDimension("dim2").getOutputDirectory(new File(".")) == new File(".", "parent/type/dim1/dim2")
        original.withRole("role", false).getOutputDirectory(new File(".")) == new File(".", "parent/role/dim1")
        original.withRole("role", true).getOutputDirectory(new File(".")) == new File(".", "parent/dim1")
        original.withBinaryName("binaryName").getOutputDirectory(new File(".")) == new File(".", "parent/binaryName")
    }

    def "ignores variant dimension with only one value"() {
        def original = createNamingScheme("parent", "role", [])
        def a = named("a")
        def b = named("b")
        def c = named("c")

        expect:
        def scheme = original.withVariantDimension(a, [a, b]).withVariantDimension(c, [c])
        scheme.variantDimensions == ["a"]
    }

    private BinaryNamingScheme createNamingScheme(def parentName, def binaryType, def dimensions) {
        def scheme = DefaultBinaryNamingScheme.component(parentName).withBinaryType(binaryType)
        dimensions.each { scheme = scheme.withVariantDimension(it) }
        return scheme
    }

    private Named named(String name) {
        return Stub(Named) {
            getName() >> name
        }
    }
}
