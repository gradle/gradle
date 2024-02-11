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

package org.gradle.integtests.tooling.r82

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import spock.lang.Issue

@TargetGradleVersion(">=4.8")
class CustomToolingModelCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        toolingApi.requireDaemons()

        file('build.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

allprojects {
    apply plugin: CustomPlugin
}

class CustomModel implements Serializable {
    List<CustomThing> things = new ArrayList()

    CustomModel(int heapSizeMb) {
        for(int i = 0; i < heapSizeMb; i++) {
            things.add(new CustomThing())
        }
    }
}

class CustomThing implements Serializable {
    byte[] payload = new byte[1024 * 1024]
}

class CustomBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == '${CustomModel.name}'
    }
    Object buildAll(String modelName, Project project) {
        return new CustomModel(50)
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
        withBuildScriptIn("a")
        withBuildScriptIn("b")
        withBuildScriptIn("c")
        withBuildScriptIn("d")
        withBuildScriptIn("e")

        settingsFile << """
include 'a', 'b', 'c', 'd', 'e'
"""
    }

    @ToolingApiVersion(">=4.8 <8.2")
    @Issue("https://github.com/gradle/gradle/issues/17810")
    def "older Tooling API versions cannot free memory when executing a build action"() {
        when:
        withConnection { connection ->
            fetchCustomModelsWithRestrictedMemoryAction(connection)
        }

        then:
        thrown(GradleConnectionException)
        caughtGradleConnectionException.cause.cause instanceof OutOfMemoryError
    }

    @ToolingApiVersion(">=8.2")
    def "can free memory when executing a build action"() {
        when:
        withConnection { connection ->
            fetchCustomModelsWithRestrictedMemoryAction(connection)
        }

        then:
        notThrown(GradleConnectionException)
        assertHasConfigureSuccessfulLogging()
    }

    private fetchCustomModelsWithRestrictedMemoryAction(ProjectConnection connection) {
        connection.action(new FetchProjectsCustomModelsAction())
            .setStandardError(stderr)
            .setStandardOutput(stdout)
            .setJvmArguments(["-Xmx256m"] + kotlinDslJvmArguments())
            .run()
    }

    private static List<String> kotlinDslJvmArguments() {
        // Having this unset is now deprecated, will default to `false` in Gradle 9.0
        // TODO remove - see https://github.com/gradle/gradle/issues/26810
        ['-Dorg.gradle.kotlin.dsl.skipMetadataVersionCheck=false']
    }
}
