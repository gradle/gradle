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
    def "generates task names for native binaries"() {
        expect:
        def namingScheme = createNamingScheme(parentName, role, dimensions)
        namingScheme.getTaskName(verb, target) == taskName

        where:
        parentName | role   | dimensions     | verb       | target    | taskName
        "test"     | ""     | []             | null       | null      | "test"
        "test"     | "type" | []             | null       | null      | "testType"
        "test"     | "type" | []             | null       | "classes" | "testTypeClasses"
        "test"     | ""     | []             | null       | "classes" | "testClasses"
        "test"     | "type" | []             | "assemble" | null      | "assembleTestType"
        "test"     | "type" | []             | "compile"  | "java"    | "compileTestTypeJava"
        "test"     | "type" | ["one", "two"] | null       | null      | "oneTwoTestType"
        "test"     | "type" | ["one", "two"] | null       | "classes" | "oneTwoTestTypeClasses"
        "test"     | "type" | ["one", "two"] | "assemble" | null      | "assembleOneTwoTestType"
        "test"     | "type" | ["one", "two"] | "compile"  | "java"    | "compileOneTwoTestTypeJava"
    }

    def "generates task name with extended inputs"() {
        expect:
        def namingScheme = createNamingScheme("theBinary", "theType", ['firstDimension', 'secondDimension'])
        namingScheme.getTaskName("theVerb", "theTarget") == "theVerbFirstDimensionSecondDimensionTheBinaryTheTypeTheTarget"
    }

    def "generates binary name"() {
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

    def "generates output directory"() {
        def namingScheme = createNamingScheme(parentName, role, dimensions, outputType)

        expect:
        namingScheme.outputDirectoryBase == outputDir

        where:
        parentName    | role         | dimensions                                 | outputType | outputDir
        "test"        | null         | []                                         | null       | "test"
        "test"        | null         | ["one", "two"]                             | null       | "test/oneTwo"
        "test"        | null         | []                                         | "jars"     | "jars/test"
        "test"        | "executable" | []                                         | null       | "testExecutable"
        "test"        | "executable" | ["one", "two"]                             | null       | "testExecutable/oneTwo"
        "mainLibrary" | "executable" | ["enterpriseEdition", "osx_x64", "static"] | null       | "mainLibraryExecutable/enterpriseEditionOsx_x64Static"
        "mainLibrary" | "executable" | ["EnterpriseEdition", "Osx_x64", "Static"] | null       | "mainLibraryExecutable/enterpriseEditionOsx_x64Static"
        "mainLibrary" | "executable" | ["EnterpriseEdition", "Osx_x64", "Static"] | "exes"     | "exes/mainLibraryExecutable/enterpriseEditionOsx_x64Static"
    }

    def "generates description"() {
        def namingScheme = createNamingScheme(parentName, typeName, dimensions)

        expect:
        namingScheme.getDescription() == lifecycleName

        where:
        parentName | typeName        | dimensions     | lifecycleName
        "parent"   | "Executable"    | []             | "executable 'parent:executable'"
        "parent"   | "SharedLibrary" | []             | "shared library 'parent:sharedLibrary'"
        "parent"   | "SharedLibrary" | ["one"]        | "shared library 'parent:one:sharedLibrary'"
        "parent"   | "SharedLibrary" | ["one", "two"] | "shared library 'parent:one:two:sharedLibrary'"
    }

    def "can create copies"() {
        def original = createNamingScheme("parent", "role", ["dim1"])

        expect:
        original.outputDirectoryBase == "parentRole/dim1"
        original.withComponentName("other").outputDirectoryBase == "otherRole/dim1"
        original.withRole("other").outputDirectoryBase == "parentOther/dim1"
        original.withVariantDimension("dim2").outputDirectoryBase == "parentRole/dim1Dim2"
        original.withOutputType("output").outputDirectoryBase == "output/parentRole/dim1"
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

    private BinaryNamingScheme createNamingScheme(def parentName, def role, def dimensions, def outputType = null) {
        return new DefaultBinaryNamingScheme(parentName, role, dimensions, outputType)
    }

    private Named named(String name) {
        return Stub(Named) {
            getName() >> name
        }
    }
}
