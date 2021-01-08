/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString


@TargetGradleVersion(">=6.8")
@LeaksFileHandles("Kotlin Compiler Daemon taking time to shut down")
class KotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "can fetch model for the init scripts of a build"() {

        given:
        def spec = withBuildSrcAndInitScripts()

        when:
        def model = kotlinDslScriptsModelFor()

        then:
        model.scriptModels.keySet() == spec.scripts.values() as Set

        and:
        assertModelMatchesBuildSpec(model, spec)
    }

    def "can fetch model for the init scripts of a build in lenient mode"() {

        given:
        def spec = withBuildSrcAndInitScripts()

        and:
        spec.scripts.init << """
            script_body_compilation_error
        """

        when:
        def model = kotlinDslScriptsModelFor(true)

        then:
        model.scriptModels.keySet() == spec.scripts.values() as Set

        and:
        assertModelMatchesBuildSpec(model, spec)

        and:
        assertHasExceptionMessage(
            model,
            spec.scripts.init,
            "Unresolved reference: script_body_compilation_error"
        )
    }

    def "can fetch model for a given set of init scripts"() {

        given:
        def spec = withBuildSrcAndInitScripts()
        def requestedScripts = spec.scripts.values()

        when:
        def model = kotlinDslScriptsModelFor(requestedScripts)

        then:
        model.scriptModels.keySet() == requestedScripts as Set

        and:
        assertModelMatchesBuildSpec(model, spec)
    }

    def "can fetch model for a given set of init scripts of a build in lenient mode"() {

        given:
        def spec = withBuildSrcAndInitScripts()
        def requestedScripts = spec.scripts.values()

        and:
        spec.scripts.init << """
            script_body_compilation_error
        """

        when:
        def model = kotlinDslScriptsModelFor(true, requestedScripts)

        then:
        model.scriptModels.keySet() == requestedScripts as Set

        and:
        assertModelMatchesBuildSpec(model, spec)

        and:
        assertHasExceptionMessage(
            model,
            spec.scripts.init,
            "Unresolved reference: script_body_compilation_error"
        )
    }

    def "single request models for init scripts equal multi requests models"() {

        given:
        def spec = withBuildSrcAndInitScripts()

        when:
        Map<File, KotlinDslScriptModel> singleRequestModels = kotlinDslScriptsModelFor().scriptModels

        and:
        Map<File, KotlinBuildScriptModel> multiRequestsModels = spec.scripts.values().collectEntries {
            [(it): kotlinBuildScriptModelFor(projectDir, it)]
        }

        then:
        spec.scripts.values().each { script ->
            assert singleRequestModels[script].classPath == multiRequestsModels[script].classPath
            assert singleRequestModels[script].sourcePath == multiRequestsModels[script].sourcePath
            assert singleRequestModels[script].implicitImports == multiRequestsModels[script].implicitImports
        }
    }

    private BuildSpec withBuildSrcAndInitScripts() {
        withBuildSrc()

        def initJar = withEmptyJar("classes_init.jar")
        def someInitJar = withEmptyJar("classes_some_init.jar")

        toolingApi.requireIsolatedUserHome()
        def gradleUserHomeDir = toolingApi.createExecuter().gradleUserHomeDir

        def init = gradleUserHomeDir.file("init.gradle.kts").tap {
            text = """
                initscript {
                    dependencies {
                        classpath(files("${escapeString(initJar)}"))
                    }
                }
            """.stripIndent()
        }

        def someInit = gradleUserHomeDir.file("init.d", "some.init.gradle.kts").tap {
            text = """
                initscript {
                    dependencies {
                        classpath(files("${escapeString(someInitJar)}"))
                    }
                }
            """.stripIndent()
        }

        return new BuildSpec(
            scripts: [
                init: init,
                someInit: someInit
            ],
            jars: [
                init: initJar,
                someInit: someInitJar
            ]
        )
    }

    private static void assertModelMatchesBuildSpec(KotlinDslScriptsModel model, BuildSpec spec) {

        model.scriptModels.values().each { script ->
            assertContainsGradleKotlinDslJars(script.classPath)
        }

        def initClassPath = canonicalClasspathOf(model, spec.scripts.init)
        assertNotContainsBuildSrc(initClassPath)
        assertContainsGradleKotlinDslJars(initClassPath)
        assertIncludes(initClassPath, spec.jars.init)

        def someInitClassPath = canonicalClasspathOf(model, spec.scripts.someInit)
        assertNotContainsBuildSrc(someInitClassPath)
        assertContainsGradleKotlinDslJars(someInitClassPath)
        assertIncludes(someInitClassPath, spec.jars.someInit)
    }
}
