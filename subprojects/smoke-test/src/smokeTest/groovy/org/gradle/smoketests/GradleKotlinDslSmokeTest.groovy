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

package org.gradle.smoketests

import org.gradle.util.Requires

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

@spock.lang.Ignore // TODO:pm temporarily ignore Kotlin DSL tests
class GradleKotlinDslSmokeTest extends AbstractSmokeTest {

    @Override
    protected String getDefaultBuildFileName() {
        'build.gradle.kts'
    }

    @Requires(KOTLIN_SCRIPT)
    def 'multi-project build with buildSrc'() {
        given:
        useSample("kotlin-dsl-multi-project-with-buildSrc")

        when:
        def result = runner('hello').build()

        then:
        result.task(':hello').outcome == SUCCESS
        result.task(':bluewhale:hello').outcome == SUCCESS
        result.task(':krill:hello').outcome == SUCCESS
    }
}
