/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r32


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import spock.lang.Issue

class ResolutionExceptionSerializableCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    def setup() {
        file('build.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

allprojects {
    apply plugin: 'java'
    apply plugin: CustomPlugin
}

class CustomArtifactModel implements Serializable {
    List<File> files
    Exception failure
}

class CustomBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == '${CustomArtifactModel.name}'
    }
    Object buildAll(String modelName, Project project) {
        try {
            List<File> compileDependencies = project.configurations.getByName('compileClasspath').files
            return new CustomArtifactModel(files: compileDependencies)
        } catch (e) {
            return new CustomArtifactModel(failure: e)
        }
    }
}

class CustomPlugin implements Plugin<Project> {
    @Inject
    CustomPlugin(ToolingModelBuilderRegistry registry) {
        registry.register(new CustomBuilder())
    }

    public void apply(Project project) {
    }
}

"""
    }

    @Issue("GRADLE-3307")
    @TargetGradleVersion(">=3.2")
    def "serializes exception when dependencies aren't resolved"() {
        when:
        file('build.gradle') << """
dependencies {
    ${implementationConfiguration} 'commons-lang:commons-lang:10.0-NOTEXISTS'
}
"""
        def customModel = withConnection { connection ->
            connection.model(CustomArtifactModel).get()
        }
        def failure = customModel.failure

        then:
        failure != null
        [failure.class.name, failure.class.superclass.name].contains("org.gradle.api.artifacts.ResolveException")
        failure.cause.toString().contains('Cannot resolve external dependency commons-lang:commons-lang:10.0-NOTEXISTS because no repositories are defined.')
    }
}
