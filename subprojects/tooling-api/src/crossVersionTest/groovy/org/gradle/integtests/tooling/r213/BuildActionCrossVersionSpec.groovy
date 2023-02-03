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

package org.gradle.integtests.tooling.r213


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r18.FetchBuildEnvironment
import org.gradle.tooling.model.build.BuildEnvironment

class BuildActionCrossVersionSpec extends ToolingApiSpecification {
    def "can use build action to retrieve BuildEnvironment model"() {
        given:
        file("settings.gradle") << 'rootProject.name="hello-world"'

        when:
        BuildEnvironment buildEnvironment = withConnection { it.action(new FetchBuildEnvironment()).run() }

        then:
        buildEnvironment.gradle.gradleVersion == targetDist.getVersion().version
        buildEnvironment.java.javaHome
        !buildEnvironment.java.jvmArguments.empty
    }
}
