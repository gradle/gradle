/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r970

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.integtests.tooling.r930.KotlinDslPluginRelatedToolingApiSpecification
import org.gradle.integtests.tooling.r940.TestResilientModelAction
import org.gradle.tooling.BuildException
import org.gradle.tooling.IntermediateResultHandler

import static org.gradle.integtests.tooling.r940.TestResilientModelAction.QueryStrategy.ROOT_BUILD_FIRST

@ToolingApiVersion('>=9.3.0')
@TargetGradleVersion('>=9.7.0')
class CustomResilientModelCrossVersionSpec extends KotlinDslPluginRelatedToolingApiSpecification {

    private static final List<String> IP_ENABLED = [
        "-Dorg.gradle.isolated-projects=true"
    ]

    def setup() {
        settingsFile.delete()
        file('init.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

gradle.lifecycle.beforeProject {
    it.plugins.apply(CustomPlugin)
}

class CustomModel implements Serializable {
    static final INSTANCE = new CustomThing()
    String getValue() { 'greetings' }
    CustomThing getThing() { return INSTANCE }
    Set<CustomThing> getThings() { return [INSTANCE] }
    Map<String, CustomThing> getThingsByName() { return [child: INSTANCE] }
    CustomThing findThing(String name) { return INSTANCE }
}

class CustomThing implements Serializable {
}

class FailingBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == '${CustomModel.name}'
    }
    Object buildAll(String modelName, Project project) {
        // The project configures successfully; the model builder itself fails for project ':b'.
        if (project.name == 'b') {
            throw new RuntimeException("Failing model builder for project ':b'")
        }
        return new CustomModel()
    }
}

class CustomPlugin implements Plugin<Project> {
    @Inject
    CustomPlugin(ToolingModelBuilderRegistry registry) {
        registry.register(new FailingBuilder())
    }

    public void apply(Project project) {
    }
}
"""
    }

    def "resilient sync propagates a model builder failure to the client when a phased build action queries a custom model#description"() {
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b", "c")
        """
        file("a").createDir()
        file("b").createDir()
        file("c").createDir()

        def capturedResult = null

        when:
        // The model builder for project ':b' fails even though configuration succeeded. Such a failure must be
        // propagated to the client as a BuildException, not silently captured as a per-project model failure.
        fails {
            action()
                .buildFinished(new TestResilientModelAction(CustomModel, ROOT_BUILD_FIRST), { capturedResult = it } as IntermediateResultHandler)
                .build()
                .withArguments("--init-script=${file('init.gradle').absolutePath}", *extraGradleProperties)
                .forTasks()
                .run()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("Failing model builder for project ':b'") }
        // Partial models are still delivered to the client before the build fails
        capturedResult.successfullyQueriedProjects == ['root', 'a', 'c']
        capturedResult.failedToQueryProjects == ['b']

        where:
        description | extraGradleProperties
        ""          | []
        " with IP"  | IP_ENABLED
    }

    def "resilient sync propagates a project configuration failure to the client when a phased build action queries a custom model#description"() {
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b", "c")
        """
        file("a").createDir()
        file("c").createDir()
        // Project ':b' fails to configure. Such a failure must be propagated to the client as a BuildException,
        // not silently captured as a per-project model failure, while partial models are still returned.
        file("b/build.gradle.kts") << """
            throw RuntimeException("Failing during project configuration")
        """

        def capturedResult = null

        when:
        fails {
            action()
                .buildFinished(new TestResilientModelAction(CustomModel, ROOT_BUILD_FIRST), { capturedResult = it } as IntermediateResultHandler)
                .build()
                .withArguments("--init-script=${file('init.gradle').absolutePath}", *extraGradleProperties)
                .forTasks()
                .run()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("Failing during project configuration") }
        // Partial models are still delivered to the client before the build fails. A configuration failure fails
        // the whole build's configuration, so every project fails to be queried.
        capturedResult.successfullyQueriedProjects == []
        capturedResult.failedToQueryProjects == ['root', 'a', 'b', 'c']

        where:
        description | extraGradleProperties
        ""          | []
        " with IP"  | IP_ENABLED
    }

    private static List<String> collectCauseMessages(Throwable throwable) {
        def messages = []
        Throwable current = throwable
        int depth = 0
        while (current != null && depth++ < 50) {
            messages << current.message
            current = current.cause
        }
        return messages
    }
}
