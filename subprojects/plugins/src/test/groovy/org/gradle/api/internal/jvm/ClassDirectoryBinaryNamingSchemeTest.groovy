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

package org.gradle.api.internal.jvm

import spock.lang.Specification

class ClassDirectoryBinaryNamingSchemeTest extends Specification {

    def "generates task names for class directory binaries"() {
        expect:
        def namer = new ClassDirectoryBinaryNamingScheme(name)
        namer.getTaskName(verb, target) == taskName

        where:
        name   | verb      | target      | taskName
        "main" | null      | null        | "main"
        "main" | "compile" | null        | "compileMain"
        "main" | null      | "resources" | "resources"
        "main" | "compile" | "java"      | "compileJava"

        "test" | null      | null        | "test"
        "test" | "compile" | null        | "compileTest"
        "test" | null      | "resources" | "testResources"
        "test" | "compile" | "java"      | "compileTestJava"
    }

    def "generates binary name and output directory"() {
        def namer = new ClassDirectoryBinaryNamingScheme(baseName)

        expect:
        namer.binaryName == binaryName
        namer.outputDirectoryBase == outputDir

        where:
        baseName | binaryName | outputDir
        "main"   | "main"     | "main"
        "test"   | "test"     | "test"
    }

    def "generates description"() {
        def namer = new ClassDirectoryBinaryNamingScheme(baseName)

        expect:
        namer.description == description

        where:
        baseName | description
        "main"   | "classes 'main'"
        "test"   | "classes 'test'"
    }
}
