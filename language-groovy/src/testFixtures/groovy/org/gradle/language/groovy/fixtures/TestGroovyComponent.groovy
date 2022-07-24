/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.groovy.fixtures

import org.gradle.integtests.fixtures.jvm.IncrementalTestJvmComponent
import org.gradle.integtests.fixtures.jvm.JvmSourceFile
import org.gradle.test.fixtures.file.TestFile

class TestGroovyComponent extends IncrementalTestJvmComponent {
    String languageName = "groovy"
    String sourceSetTypeName = "GroovySourceSet"

    List<JvmSourceFile> sources = [
        new JvmSourceFile("compile/test", "Person.groovy", '''
package compile.test

import java.util.Arrays

class Person {
    String name
    int age

    void hello() {
        Iterable<Integer> vars = Arrays.asList(3, 1, 2).sort()
    }
}'''),
        new JvmSourceFile("compile/test", "Person2.groovy", '''
package compile.test

public class Person2 {
}
''')
    ]


    @Override
    void changeSources(List<TestFile> sourceFiles){
        def personGroovyFile = sourceFiles.find { it.name == "Person.groovy" }
        personGroovyFile.text = personGroovyFile.text.replace("String name", "String name\\nString anotherName")
    }

    @Override
    void writeAdditionalSources(TestFile sourceDir) {
        sourceDir.file("java/Extra.groovy") << """
interface Extra {
    String whatever()
}
"""
    }

    List<JvmSourceFile> expectedOutputs = [
            sources[0].classFile,
            sources[1].classFile,
            resources[0],
            resources[1]
    ]

    @Override
    TestFile createIgnoredFileInSources(TestFile sourceDir) {
        sourceDir.createFile("java/SomeIgnoredFile.groovy~") << '// this file should be ignored'
    }
}
