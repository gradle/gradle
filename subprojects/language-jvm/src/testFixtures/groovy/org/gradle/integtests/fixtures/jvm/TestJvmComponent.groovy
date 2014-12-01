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

    abstract List<TestFile> writeSources(TestFile testFile)

    abstract String getLanguageName()

    abstract void changeSources(List<TestFile> testFiles)

    abstract void writeAdditionalSources(TestFile testFile)

    List<JvmSourceFile> resources = [
            new JvmSourceFile("", "one.txt", "Here is a resource"),
            new JvmSourceFile("sub-dir", "two.txt", "Here is another resource")
    ]

    abstract List<JvmSourceFile> getExpectedOutputs();

    List<TestFile> writeResources(TestFile testFile) {
        return resources*.writeToDir(testFile)
    }

}