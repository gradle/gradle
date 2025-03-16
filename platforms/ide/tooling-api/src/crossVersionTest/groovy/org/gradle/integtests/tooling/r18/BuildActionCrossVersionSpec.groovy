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

import org.gradle.integtests.tooling.fixture.ActionQueriesModelThatRequiresConfigurationPhase
import org.gradle.integtests.tooling.fixture.ActionQueriesModelThatRequiresOnlySettingsEvaluation
import org.gradle.integtests.tooling.fixture.ActionShouldNotBeCalled
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildException
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.model.idea.IdeaProject

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

    @TargetGradleVersion(">=5.1")
    def "client receives the exception thrown by the build action"() {
        when:
        withConnection {
            it.action(new BrokenAction()).run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message == /The supplied build action failed with an exception./
        e.cause instanceof BrokenAction.CustomException

        and:
        failure.assertHasDescription('this is a custom exception')
        assertHasConfigureFailedLogging()
    }

    @TargetGradleVersion(">=3.0 <5.1")
    def "client receives the exception thrown by the build action for version that does not log failure"() {
        when:
        withConnection {
            def action = it.action(new BrokenAction())
            action.run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message == /The supplied build action failed with an exception./
        e.cause instanceof BrokenAction.CustomException
    }

    @TargetGradleVersion(">=5.1")
    def "client receives the exception thrown when action requests unknown model"() {
        when:
        withConnection {
            it.action(new FetchUnknownModel()).run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message == /The supplied build action failed with an exception./
        e.cause instanceof UnknownModelException

        and:
        failure.assertHasDescription("No model of type 'CustomModel' is available in this build.")
        assertHasConfigureFailedLogging()
    }

    @TargetGradleVersion(">=3.0 <5.1")
    def "client receives the exception thrown when action requests unknown model for version that does not log failure"() {
        when:
        withConnection {
            it.action(new FetchUnknownModel()).run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message == /The supplied build action failed with an exception./
        e.cause instanceof UnknownModelException
    }

    @TargetGradleVersion(">=3.0 <7.3")
    def "does not run action when configuration fails"() {
        given:
        buildFile << 'throw new RuntimeException("broken")'

        when:
        withConnection {
            it.action(new ActionShouldNotBeCalled()).run()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith('Could not run build action using')
        e.cause.message.contains('A problem occurred evaluating root project')

        and:
        failure.assertHasDescription('A problem occurred evaluating root project')
        assertHasConfigureFailedLogging()
    }

    @TargetGradleVersion(">=7.3")
    def "action receives failure when init script fails"() {
        given:
        def initScript = file("init.gradle")
        initScript << 'throw new RuntimeException("broken")'

        when:
        withConnection {
            it.action(new ActionQueriesModelThatRequiresOnlySettingsEvaluation())
                .withArguments("-I${initScript}")
                .run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message.startsWith('The supplied build action failed with an exception.')
        e.cause.message.contains('A problem occurred evaluating initialization script.')

        and:
        failure.assertHasDescription('A problem occurred evaluating initialization script.')
        assertHasConfigureFailedLogging()
    }

    @TargetGradleVersion(">=7.3")
    def "action receives failure when settings evaluation fails"() {
        given:
        settingsFile << '''
            rootProject.name = 'root'
            throw new RuntimeException("broken")
        '''

        when:
        withConnection {
            it.action(new ActionQueriesModelThatRequiresOnlySettingsEvaluation())
                .run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message.startsWith('The supplied build action failed with an exception.')
        e.cause.message.contains("A problem occurred evaluating settings 'root'.")

        and:
        failure.assertHasDescription("A problem occurred evaluating settings 'root'.")
        assertHasConfigureFailedLogging()
    }

    @TargetGradleVersion(">=7.3")
    def "action receives failure when project configuration fails"() {
        given:
        buildFile << 'throw new RuntimeException("broken")'

        when:
        withConnection {
            it.action(new ActionQueriesModelThatRequiresConfigurationPhase())
                .run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message.startsWith('The supplied build action failed with an exception.')
        e.cause.message.contains('A problem occurred configuring root project')

        and:
        failure.assertHasDescription('A problem occurred evaluating root project')
        assertHasConfigureFailedLogging()
    }
}
