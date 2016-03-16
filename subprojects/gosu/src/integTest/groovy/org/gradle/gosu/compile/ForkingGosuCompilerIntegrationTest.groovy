/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.gosu.compile

import org.gradle.integtests.fixtures.GosuCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@TargetCoverage({GosuCoverage.DEFAULT})
@Requires(TestPrecondition.JDK8_OR_LATER)
@LeaksFileHandles
class ForkingGosuCompilerIntegrationTest extends BasicGosuCompilerIntegrationTest {
    @Rule TestResources testResources = new TestResources(temporaryFolder)

    String compilerConfiguration() {
        """
compileGosu.gosuCompileOptions.with {
    useAnt = false
    fork = true
}
        """
    }

    String logStatement() {
        "Compiling with the Forking Gosu compiler."
    }

    def compilesGosuCode() {
        when:
        def personSource = file("build/classes/main/Person.gs")
        def personBytecode = file("build/classes/main/Person.class")
        def houseSource = file("build/classes/main/House.gs")
        def houseBytecode = file("build/classes/main/House.class")
        def otherSource = file("build/classes/main/Other.gs")
        def otherBytecode = file("build/classes/main/Other.class")
        run('compileGosu')

        then:
        personSource.exists()
        personBytecode.exists()
        houseSource.exists()
        houseBytecode.exists()
        otherSource.exists()
        otherBytecode.exists()
    }
}
