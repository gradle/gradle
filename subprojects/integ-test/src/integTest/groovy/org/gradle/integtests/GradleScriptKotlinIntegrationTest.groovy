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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import spock.lang.Issue

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT
import static org.gradle.util.TestPrecondition.NOT_WINDOWS

// TODO: to make it work on Windows disable IDEA's native win32 filesystem
//   https://github.com/JetBrains/kotlin/blob/167ab1f860fc8a3541feb3d3b1c895ef26b5abae/compiler/cli/src/org/jetbrains/kotlin/cli/common/CLICompiler.java#L52
//   Might be something better done at the gradle-script-kotlin side

@Issue("https://github.com/gradle/gradle-script-kotlin/issues/154")
@Requires([KOTLIN_SCRIPT, NOT_WINDOWS])
class GradleScriptKotlinIntegrationTest extends AbstractIntegrationSpec {

    @Override
    protected String getDefaultBuildFileName() {
        'build.gradle.kts'
    }

    def setup() {
        settingsFile << "rootProject.buildFileName = '$defaultBuildFileName'"
    }

    def 'can run a simple task'() {
        given:
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            
            open class SimpleTask : DefaultTask() {
                @TaskAction fun run() = println("it works!")
            }

            task<SimpleTask>("build")
        """

        when:
        run 'build'

        then:
        result.output.contains('it works!')
    }

    def 'can query KotlinBuildScriptModel'() {
        given:
        // This test breaks encapsulation a bit in the interest of ensuring Gradle Script Kotlin use
        // of internal APIs is not broken by refactorings on the Gradle side
        buildFile << """
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.script.lang.kotlin.resolver.KotlinBuildScriptModel
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

task("dumpKotlinBuildScriptModelClassPath") {
    doLast {
        val modelName = KotlinBuildScriptModel::class.qualifiedName
        val builderRegistry = (project as ProjectInternal).services[ToolingModelBuilderRegistry::class.java]
        val builder = builderRegistry.getBuilder(modelName)
        val model = builder.buildAll(modelName, project) as KotlinBuildScriptModel
        if (model.classPath.any { it.name.startsWith("gradle-script-kotlin") }) {
            println("gradle-script-kotlin!")
        }
    }
}
        """

        when:
        run 'dumpKotlinBuildScriptModelClassPath'

        then:
        result.output.contains("gradle-script-kotlin!")
    }
}
