/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.tooling.r18

import org.gradle.integtests.tooling.fixture.NullAction
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.idea.IdeaProject

@TargetGradleVersion('>=1.8')
class BuildActionCrossVersionSpec extends ToolingApiSpecification {
    def "client receives the result of running a build action"() {
        given:
        file("settings.gradle") << 'rootProject.name="hello-world"'

        when:
        CustomModel customModel = withConnection { it.action(new FetchCustomModel()).run() }

        then:
        customModel.gradle.name == "hello-world"
        customModel.eclipse.gradleProject.name == "hello-world"

        when:
        IdeaProject ideaModel = withConnection { it.action(new FetchIdeaModel()).run() }

        then:
        ideaModel.name == "hello-world"
        ideaModel.modules.size() == 1

        when:
        def nullModel = withConnection { it.action(new NullAction()).run() }

        then:
        nullModel == null
    }

    @TargetGradleVersion(">=2.2")
    def "action classes are reused in daemon"() {
        toolingApi.requireIsolatedDaemons()

        expect:
        def result1 = withConnection { it.action(new CounterAction()).run() }
        def result2 = withConnection { it.action(new CounterAction()).run() }
        def result3 = withConnection { it.action(new CounterAction()).run() }
        result1 == 1
        result2 == 2
        result3 == 3
    }

    @TargetGradleVersion(">=1.8 <=2.1")
    def "action classes are reused in daemon when daemon is idle when operation starts"() {
        toolingApi.requireIsolatedDaemons()

        expect:
        def result1 = withConnection { it.action(new CounterAction()).run() }

        // Earlier versions return the build result before marking the daemon as idle. Wait for the daemon to be marked as idle
        // before attempting the next operation otherwise the client will start a new daemon
        toolingApi.daemons.daemon.becomesIdle()

        def result2 = withConnection { it.action(new CounterAction()).run() }
        toolingApi.daemons.daemon.becomesIdle()

        def result3 = withConnection { it.action(new CounterAction()).run() }
        result1 == 1
        result2 == 2
        result3 == 3
    }

    def "client receives the exception thrown by the build action"() {
        when:
        withConnection { it.action(new BrokenAction()).run() }

        then:
        BuildActionFailureException e = thrown()
        e.message == /The supplied build action failed with an exception./
        e.cause instanceof BrokenAction.CustomException
    }

    def "client receives the exception thrown when action requests unknown model"() {
        when:
        withConnection { it.action(new FetchUnknownModel()).run() }

        then:
        // Verification is in the action
        noExceptionThrown()
    }

    def "client receives the exception thrown when build fails"() {
        given:
        buildFile << 'throw new RuntimeException("broken")'

        when:
        withConnection { it.action(new FetchCustomModel()).run() }

        then:
        // TODO:ADAM - clean this up
        BuildException e = thrown()
        e.message.startsWith('Could not run build action using')
    }

    @ToolingApiVersion('current')
    @TargetGradleVersion('>=1.2 <1.8')
    def "gives reasonable error message when target Gradle version does not support build actions"() {
        when:
        withConnection { it.action(new FetchCustomModel()).run() }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (${targetDist.version.version}) does not support the BuildActionExecuter API. Support for this is available in Gradle 1.8 and all later versions."
    }
}
