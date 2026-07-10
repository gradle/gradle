/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.swift

import groovy.json.JsonSlurper
import spock.lang.Specification

import java.nio.file.Files

class SwiftCompilerTest  extends Specification {

    def "Entry's members are used to write json file"() {
        def map = new SwiftCompiler.OutputFileMap()
        def b = map.newEntry("entry");

        def dependencyFile = Files.createTempFile("dependencyFile", "").toFile()
        b.dependencyFile(dependencyFile)

        def output = Files.createTempFile("writeOutputFile", "")
        map.writeToFile(output.toFile())
        expect:
        Files.exists(output)
        def json = new JsonSlurper().parse(output)
        json.entry.dependencies == dependencyFile.getAbsolutePath()
    }
}
