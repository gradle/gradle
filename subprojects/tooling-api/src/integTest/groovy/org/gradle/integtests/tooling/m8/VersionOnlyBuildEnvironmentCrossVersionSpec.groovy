/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.m8

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.build.BuildEnvironment

@ToolingApiVersion('>=1.0-milestone-8')
@TargetGradleVersion('<=1.0-milestone-7')
class VersionOnlyBuildEnvironmentCrossVersionSpec extends ToolingApiSpecification {

    def "informs about version"() {
        when:
        BuildEnvironment model = withConnection { it.getModel(BuildEnvironment.class) }

        then:
        model.gradle.gradleVersion == targetDist.version.version
    }

    def "fails gracefully for other info"() {
        given:
        BuildEnvironment model = withConnection { it.getModel(BuildEnvironment.class) }

        when:
        model.java.javaHome
        then:
        def ex = thrown(UnsupportedMethodException)
        ex instanceof UnsupportedOperationException //backwards compatibility

        when:
        model.java.jvmArguments
        then:
        def e = thrown(UnsupportedMethodException)
        e instanceof UnsupportedOperationException //backwards compatibility
    }
}
