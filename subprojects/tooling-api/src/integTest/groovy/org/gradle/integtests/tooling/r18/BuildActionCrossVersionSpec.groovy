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
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.BuildException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.idea.IdeaProject

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

        when:
        def nullModel = withConnection { it.action(new NullAction()).run() }

        then:
        nullModel == null
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

    def causes(Throwable throwable) {
        def causes = []
        for (def c = throwable.cause; c != null; c = c.cause) {
            causes << c
        }
        return causes
    }

    @ToolingApiVersion('current')
    @TargetGradleVersion('<1.8')
    def "gives reasonable error message when target Gradle version does not support build actions"() {
        when:
        withConnection { it.action(new FetchCustomModel()).run() }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (${targetDist.version.version}) does not support execution of build actions provided by the tooling API client. Support for this was added in Gradle 1.8 and is available in all later versions."
    }
}
