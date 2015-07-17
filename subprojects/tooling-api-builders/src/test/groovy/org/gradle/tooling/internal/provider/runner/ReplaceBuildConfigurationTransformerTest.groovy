/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner
import org.gradle.execution.BuildConfigurationAction
import org.gradle.execution.BuildExecutionContext
import spock.lang.Specification

class ReplaceBuildConfigurationTransformerTest extends Specification {

    def "adds custom specific build configuration action"() {
        given:
        BuildConfigurationAction buildConfigurationAction = Mock()
        def transformer = new ReplaceBuildConfigurationTransformer(buildConfigurationAction, [])
        when:
        def outputList = transformer.transform([])
        then:
        outputList == [buildConfigurationAction]
    }

    def "removes buildconfiguration actions"() {
        given:
        BuildConfigurationAction customBuildConfiguration = Mock()
        def transformer = new ReplaceBuildConfigurationTransformer(customBuildConfiguration, [TestBuildConfigurationAction1, TestBuildConfigurationAction2])

        def givenAction1 = Mock(TestBuildConfigurationAction1)
        def givenAction2 = Mock(TestBuildConfigurationAction2)
        def givenAction3 = Mock(TestBuildConfigurationAction3)
        when:
        def outputList = transformer.transform([givenAction1, givenAction2, givenAction3])
        then:
        outputList == [givenAction3, customBuildConfiguration]
    }

    class TestBuildConfigurationAction1 implements BuildConfigurationAction{
        @Override
        void configure(BuildExecutionContext context) {

        }
    }

    class TestBuildConfigurationAction2 implements BuildConfigurationAction{
        @Override
        void configure(BuildExecutionContext context) {

        }
    }

    class TestBuildConfigurationAction3 implements BuildConfigurationAction{
        @Override
        void configure(BuildExecutionContext context) {

        }
    }
}
