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
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.idea.IdeaProject
import spock.lang.Ignore

@ToolingApiVersion('>=1.8')
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
    }

    @Ignore("work in progress")
    def "client receives the exception thrown by the build action"() {
        when:
        withConnection { it.action(new BrokenAction()).run() }

        then:
        GradleConnectionException e = thrown()
        e.cause instanceof BrokenAction.CustomException
    }

    @TargetGradleVersion('<1.8')
    def "gives reasonable error message when target Gradle version does not support build actions"() {
        when:
        maybeFailWithConnection { it.action(new FetchCustomModel()).run() }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (${targetDist.version.version}) does not support build actions."
    }
}
