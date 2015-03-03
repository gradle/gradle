/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.exec

import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.initialization.*
import spock.lang.Specification

class InProcessBuildActionExecuterTest extends Specification {
    final GradleLauncherFactory factory = Mock()
    final DefaultGradleLauncher launcher = Mock()
    final BuildRequestContext buildRequestContext = Mock()
    final BuildActionParameters param = Mock()
    final BuildRequestMetaData metaData = Mock()
    final BuildResult buildResult = Mock()
    final GradleInternal gradle = Mock()
    final BuildActionRunner actionRunner = Mock()
    final InProcessBuildActionExecuter executer = new InProcessBuildActionExecuter(factory, actionRunner)

    def setup() {
        _ * param.buildRequestMetaData >> metaData
    }

    def "does nothing when action does not use the build"() {
        BuildAction action = Mock()

        when:
        def result = executer.execute(action, buildRequestContext, param)

        then:
        result == '<result>'

        and:
        1 * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            return '<result>'
        }
    }

    def "creates a launcher using a default StartParameter when the action does not specify any"() {
        BuildAction action = Mock()

        when:
        def result = executer.execute(action, buildRequestContext, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(!null, buildRequestContext) >> launcher
        1 * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            assert controller.launcher == launcher
            return '<result>'
        }
        1 * launcher.stop()
    }

    def "creates a launcher using StartParameter specified by the action"() {
        BuildAction action = Mock()
        def startParam = new StartParameter()

        when:
        def result = executer.execute(action, buildRequestContext, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(startParam, buildRequestContext) >> launcher
        1 * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            controller.startParameter = startParam
            assert controller.launcher == launcher
            return '<result>'
        }
        1 * launcher.stop()
    }

    def "cannot set start parameters after launcher created"() {
        BuildAction action = Mock()
        def startParam = new StartParameter()

        given:
        _ * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            controller.launcher
            controller.startParameter = startParam
        }
        _ * factory.newInstance(!null, buildRequestContext) >> launcher

        when:
        executer.execute(action, buildRequestContext, param)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot change start parameter after build has started.'

        and:
        1 * launcher.stop()
    }

    def "creates launcher when Gradle instance is requested"() {
        BuildAction action = Mock()

        when:
        def result = executer.execute(action, buildRequestContext, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(!null, buildRequestContext) >> launcher
        1 * launcher.getGradle() >> gradle
        _ * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            assert controller.getGradle() == gradle
            return '<result>'
        }
        1 * launcher.stop()
    }

    def "runs build when requested by action"() {
        BuildAction action = Mock()

        when:
        def result = executer.execute(action, buildRequestContext, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(!null, buildRequestContext) >> launcher
        1 * launcher.run() >> buildResult
        _ * buildResult.failure >> null
        _ * buildResult.gradle >> gradle
        _ * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            assert controller.run() == gradle
            return '<result>'
        }
        1 * launcher.stop()
    }

    def "configures build when requested by action"() {
        BuildAction action = Mock()

        when:
        def result = executer.execute(action, buildRequestContext, param)

        then:
        result == '<result>'

        and:
        1 * factory.newInstance(!null, buildRequestContext) >> launcher
        1 * launcher.getBuildAnalysis() >> buildResult
        _ * buildResult.failure >> null
        _ * buildResult.gradle >> gradle
        _ * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            assert controller.configure() == gradle
            return '<result>'
        }
        1 * launcher.stop()
    }

    def "cannot request configuration after build has been run"() {
        BuildAction action = Mock()

        given:
        actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            controller.run()
            controller.configure()
        }

        when:
        executer.execute(action, buildRequestContext, param)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use launcher after build has completed.'

        and:
        1 * factory.newInstance(!null, buildRequestContext) >> launcher
        1 * launcher.run() >> buildResult
        1 * launcher.stop()
    }

    def "wraps build failure"() {
        def failure = new RuntimeException()
        BuildAction action = Mock()

        given:
        buildResult.failure >> failure

        when:
        executer.execute(action, buildRequestContext, param)

        then:
        ReportedException e = thrown()
        e.cause == failure

        and:
        1 * factory.newInstance(!null, buildRequestContext) >> launcher
        1 * launcher.run() >> buildResult
        _ * actionRunner.run(action, !null) >> { BuildAction a, BuildController controller ->
            controller.run()
        }
        1 * launcher.stop()
    }
}
