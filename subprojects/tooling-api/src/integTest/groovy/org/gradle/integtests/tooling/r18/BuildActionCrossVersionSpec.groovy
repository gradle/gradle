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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.UnsupportedVersionException
import spock.lang.Ignore

@ToolingApiVersion('>=1.8')
@TargetGradleVersion('>=1.8')
class BuildActionCrossVersionSpec extends ToolingApiSpecification {
    def "client receives the result of running a build action"() {
        when:
        String result = withConnection { it.action(new CustomAction(message: "hello world")).run() }

        then:
        result == "hello world"
    }

    @Ignore
    def "client receives the exception thrown by the build action"() {
        when:
        withConnection { it.action(new BrokenAction()).run() }

        then:
        GradleConnectionException e = thrown()
        e.cause instanceof CustomException
    }

    @Ignore
    @TargetGradleVersion('<1.8')
    def "gives reasonable error message when target Gradle version does not support build actions"() {
        when:
        withConnection { it.action(new CustomAction(message: "hello world")).run() }

        then:
        UnsupportedVersionException e = thrown()
        e.message == '??'
    }

    static class CustomAction implements BuildAction<String> {
        String message

        def String execute(BuildController controller) {
            return message
        }
    }

    static class CustomException extends RuntimeException {}

    static class BrokenAction implements BuildAction<String> {
        String execute(BuildController controller) {
            throw new CustomException()
        }
    }
}
