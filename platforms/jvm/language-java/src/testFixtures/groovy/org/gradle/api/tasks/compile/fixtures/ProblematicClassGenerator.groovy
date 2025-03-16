/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.tasks.compile.fixtures


import org.gradle.test.fixtures.file.TestFile

class ProblematicClassGenerator {
    private final TestFile sourceFile
    private int warningIndex = 0
    private int errorIndex = 0

    ProblematicClassGenerator(TestFile root, String className = "Foo", String sourceSet = "main") {
        this.sourceFile = root.file("src/${sourceSet}/java/${className}.java")

        // Get the class name from the file name
        this.sourceFile << """\
public class ${className} {

"""
    }

    void addWarning() {
        warningIndex += 1
        sourceFile << """\
    public void warning${warningIndex}() {
        // Unnecessary cast will trigger a warning
        String s = (String)"Hello World";
    }
"""
    }

    void addError() {
        errorIndex += 1
        sourceFile << """\
public void error${errorIndex}() {
    // Missing semicolon will trigger an error
    String s = "Hello, World!"
}
"""
    }

    TestFile save() throws Exception {
        sourceFile << """\

}"""
        return sourceFile
    }
}
