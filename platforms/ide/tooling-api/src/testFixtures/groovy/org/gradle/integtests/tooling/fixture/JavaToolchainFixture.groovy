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

package org.gradle.integtests.tooling.fixture

import groovy.transform.SelfType
import org.gradle.api.JavaVersion
import org.gradle.test.fixtures.file.TestFile

@SelfType(ToolingApiSpecification)
trait JavaToolchainFixture {

    void outputContains(String string) {
        assertHasResult()
        result.assertOutputContains(string.trim())
    }

    private assertHasResult() {
        assert result != null: "result is null, you haven't run succeeds()"
    }

    /**
     * Returns the Java version from the compiled class bytecode.
     */
    JavaVersion classJavaVersion(File classFile) {
        assert classFile.exists()
        return JavaVersion.forClass(classFile.bytes)
    }

    TestFile javaClassFile(String fqcn) {
        classFile("java", "main", fqcn)
    }

    TestFile classFile(String language, String sourceSet, String fqcn) {
        file("build/classes/", language, sourceSet, fqcn)
    }
}
