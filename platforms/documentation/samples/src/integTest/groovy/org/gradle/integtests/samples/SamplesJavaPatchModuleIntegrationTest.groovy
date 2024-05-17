/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

@Requires(UnitTestPreconditions.Jdk9OrLater)
class SamplesJavaPatchModuleIntegrationTest extends AbstractIntegrationSpec {

    @Rule Sample sample = new Sample(temporaryFolder, 'testing/patch-module')

    def setup() {
        executer.withRepositoryMirrors()
    }

    def "can compile and run patched module whitebox tests with #dsl dsl"() {
        when:
        executer.inDirectory(sample.dir.file(dsl))
        succeeds('build')

        then:
        executed ':compileTestJava', ':test'

        where:
        dsl << ['groovy', 'kotlin']
    }

}
