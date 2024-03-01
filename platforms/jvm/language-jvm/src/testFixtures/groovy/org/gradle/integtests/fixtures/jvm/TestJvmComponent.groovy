/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.fixtures.jvm

import org.gradle.test.fixtures.file.TestFile

abstract class TestJvmComponent {
    abstract List<JvmSourceFile> getSources()

    abstract String getLanguageName()

    List<JvmSourceFile> getExpectedClasses() {
        return getSources().collect{it.classFile}
    }

    List<JvmSourceFile> getExpectedOutputs(){
        return getSources().collect { it.classFile } + resources;
    }

    List<TestFile> writeSources(TestFile sourceDir, String sourceSetName = languageName) {
        return sources*.writeToDir(sourceDir.file(sourceSetName))
    }

    List<JvmSourceFile> resources = [
            new JvmSourceFile("", "one.txt", "Here is a resource"),
            new JvmSourceFile("sub-dir", "two.txt", "Here is another resource")
    ]

    List<TestFile> writeResources(TestFile testFile) {
        return resources*.writeToDir(testFile)
    }

    String getSourceSetTypeName() {
        "JavaSourceSet"
    }

    def getSourceFileExtensions() {
        return [getLanguageName()]
    }
}
