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

package org.gradle.testkit.runner

import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion

@NonCrossVersion
@NoDebug
class GradleRunnerConfigurationIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "throws exception if no project dir was specified"() {
        given:
        def runner = GradleRunner.create().withTestKitDir(testKitDir)

        when:
        runner.build()

        then:
        def t = thrown InvalidRunnerConfigurationException
        t.message == 'Please specify a project directory before executing the build'

        when:
        runner.buildAndFail()

        then:
        t = thrown InvalidRunnerConfigurationException
        t.message == 'Please specify a project directory before executing the build'
    }

}
