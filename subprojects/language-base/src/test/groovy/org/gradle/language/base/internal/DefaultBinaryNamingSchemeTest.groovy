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

package org.gradle.language.base.internal

import spock.lang.Specification

class DefaultBinaryNamingSchemeTest extends Specification {
    def "generates task names"() {
        expect:
        def namer = new DefaultBinaryNamingScheme(baseName, dimensions)
        namer.getTaskName(verb, target) == taskName

        where:
        baseName | dimensions     | verb       | target    | taskName
        "test"   | []             | null       | null      | "test"
        "test"   | []             | null       | "classes" | "testClasses"
        "test"   | []             | "assemble" | null      | "assembleTest"
        "test"   | []             | "compile"  | "java"    | "compileTestJava"
        "test"   | ["one", "two"] | null       | null      | "oneTwoTest"
        "test"   | ["one", "two"] | null       | "classes" | "oneTwoTestClasses"
        "test"   | ["one", "two"] | "assemble" | null      | "assembleOneTwoTest"
        "test"   | ["one", "two"] | "compile"  | "java"    | "compileOneTwoTestJava"
    }

    def "generates task name with extended inputs"() {
        expect:
        def namer = new DefaultBinaryNamingScheme("theBinary", ["firstDimension", "secondDimension"])
        namer.getTaskName("theVerb", "theTarget") == "theVerbFirstDimensionSecondDimensionTheBinaryTheTarget"
    }

    def "generates base name and output directory"() {
        def namer = new DefaultBinaryNamingScheme(baseName, dimensions)

        expect:
        namer.getLifecycleTaskName() == lifecycleName
        namer.getOutputDirectoryBase() == outputDir

        where:
        baseName | dimensions     | lifecycleName | outputDir
        "test"   | []             | "test"        | "test"
        "test"   | ["one", "two"] | "oneTwoTest"  | "test/one/two"
        "mainLibrary"| ["enterpriseEdition", "osx_x64", "static"] | "enterpriseEditionOsx_x64StaticMainLibrary"  | "mainLibrary/enterpriseEdition/osx_x64/static"
        "mainLibrary"| ["EnterpriseEdition", "Osx_x64", "Static"] | "enterpriseEditionOsx_x64StaticMainLibrary"  | "mainLibrary/enterpriseEdition/osx_x64/static"
    }

    def "can collapse `main` when generating names"() {
        expect:
        def namer = new CollapsedNamingScheme(name, dimensions)
        namer.getTaskName(verb, target) == taskName

        where:
        name   | dimensions     | verb       | target    | taskName
        "main" | []             | null       | null      | "main"
        "main" | []             | null       | "classes" | "classes"
        "main" | ["one", "two"] | null       | "classes" | "oneTwoClasses"
        "main" | []             | "assemble" | null      | "assembleMain"
        "main" | ["one", "two"] | "assemble" | null      | "assembleOneTwoMain"
        "main" | []             | "compile"  | "java"    | "compileJava"
        "main" | ["one", "two"] | "compile"  | "java"    | "compileOneTwoJava"
        "test" | []             | null       | "classes" | "testClasses"
        "test" | []             | "assemble" | null      | "assembleTest"
    }

    private static class CollapsedNamingScheme extends DefaultBinaryNamingScheme {
        CollapsedNamingScheme(String baseName, List<String> dimensions) {
            super(baseName, dimensions)
            collapseMain()
        }
    }
}
