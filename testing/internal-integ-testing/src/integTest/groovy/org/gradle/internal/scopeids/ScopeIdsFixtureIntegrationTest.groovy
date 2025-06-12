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

package org.gradle.internal.scopeids

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.OtherGradleVersionFixture
import org.gradle.integtests.fixtures.ScopeIdsFixture
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.junit.Rule

class ScopeIdsFixtureIntegrationTest extends AbstractIntegrationSpec implements OtherGradleVersionFixture {

    GradleExecuter previousExecuter

    @Rule
    ScopeIdsFixture scopeIds = new ScopeIdsFixture(executer, temporaryFolder)

    def setup() {
        previousExecuter = otherVersion.executer(temporaryFolder, buildContext) as AbstractGradleExecuter
        scopeIds.configureExecuter(previousExecuter)
    }

    void runCurrent() {
        succeeds("help")
    }

    void runEarlier() {
        previousExecuter.withTasks("help").run()
    }

    def "reads ids written by earlier versions"() {
        when:
        runEarlier()
        runCurrent()

        then:
        assertIdsAreShared(scopeIds.ids(0), scopeIds.ids(1))
    }

    def "writes ids readable by earlier versions"() {
        when:
        runCurrent()
        runEarlier()

        then:
        assertIdsAreShared(scopeIds.ids(1), scopeIds.ids(0))
    }

    void assertIdsAreShared(ScopeIdsFixture.ScopeIds earlier, ScopeIdsFixture.ScopeIds current) {
        assert current.buildInvocation != earlier.buildInvocation
        assert current.workspace == earlier.workspace
        assert current.user == earlier.user
    }

}
