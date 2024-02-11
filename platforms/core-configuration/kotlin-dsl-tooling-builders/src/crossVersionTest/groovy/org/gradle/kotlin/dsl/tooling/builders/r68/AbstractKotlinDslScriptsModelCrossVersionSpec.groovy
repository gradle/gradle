/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.integtests.fixtures.build.BuildSpec
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString

class AbstractKotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    protected BuildSpec withBuildSrcAndInitScripts() {
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
                someInit: someInit,
                settings: file("settings.gradle.kts")
            ],
            jars: [
                init: initJar,
                someInit: someInitJar
            ]
        )
    }

    protected static void assertModelMatchesBuildSpec(KotlinDslScriptsModel model, BuildSpec spec) {

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
