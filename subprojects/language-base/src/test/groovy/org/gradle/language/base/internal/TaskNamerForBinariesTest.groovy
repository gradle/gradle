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

class TaskNamerForBinariesTest extends Specification {
    def "generates task names"() {
        def namer = new TaskNamerForBinaries("test")

        expect:
        namer.getTaskName(verb, target) == taskName

        where:
        verb       | target    | taskName
        null       | null      | "test"
        null       | "classes" | "testClasses"
        "assemble" | null      | "assembleTest"
        "compile"  | "java"    | "compileTestJava"
    }

    def "can collapse `main` when generating names"() {
        expect:
        def namer = TaskNamerForBinaries.collapseMain(name)
        namer.getTaskName(verb, target) == taskName

        where:
        name   | verb       | target    | taskName
        "main" | null       | null      | "main"
        "main" | null       | "classes" | "classes"
        "main" | "assemble" | null      | "assembleMain"
        "main" | "compile"  | "java"    | "compileJava"
        "test" | null       | "classes" | "testClasses"
        "test" | "assemble" | null      | "assembleTest"
    }

    def "generates output directory names"() {
        expect:
        def namer = new TaskNamerForBinaries(name)
        namer.outputDirectoryBase == outputDirName

        where:
        name   | outputDirName
        "main" | "main"
    }
}
