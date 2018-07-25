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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.junit.Rule
import spock.lang.IgnoreIf

/**
 * This specification runs a java plugin based build that defines additional source sets for building independent jars with
 * the full lifecycle and running independent test tasks. Such build allows to exercise running multiple tasks from a single project in parallel.
 */
@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ParallelJavaPluginTest extends AbstractSampleIntegrationTest {
    @Rule
    TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        executer.withArgument("--parallel")
                .withArgument("--max-workers=4")
    }

    def "can execute a java build that runs tasks in parallel"() {
        expect:
        succeeds "build"
    }
}
