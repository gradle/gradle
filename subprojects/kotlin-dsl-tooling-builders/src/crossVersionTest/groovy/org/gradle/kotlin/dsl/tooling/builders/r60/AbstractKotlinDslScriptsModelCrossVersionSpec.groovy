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

package org.gradle.kotlin.dsl.tooling.builders.r60

import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString

class AbstractKotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    protected BuildSpec withMultiProjectBuildWithBuildSrc() {
        withBuildSrc()
        def someJar = withEmptyJar("classes_some.jar")
        def settingsJar = withEmptyJar("classes_settings.jar")
        def rootJar = withEmptyJar("classes_root.jar")
        def aJar = withEmptyJar("classes_a.jar")
        def bJar = withEmptyJar("classes_b.jar")
        def precompiledJar = withEmptyJar("classes_b_precompiled.jar")

        def some = withFile("some.gradle.kts", """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(someJar)}"))
                }
            }
        """)
        def settings = withSettings("""
            buildscript {
                dependencies {
                    classpath(files("${escapeString(settingsJar)}"))
                }
            }
            apply(from = "some.gradle.kts")
            include("a", "b")
        """)
        def root = withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("${escapeString(rootJar)}"))
                }
            }
            apply(from = "some.gradle.kts")
        """)
        def a = withBuildScriptIn("a", """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(aJar)}"))
                }
            }
            apply(from = "../some.gradle.kts")
        """)
        def b = withBuildScriptIn("b", """
            plugins {
                `kotlin-dsl`
            }
            buildscript {
                dependencies {
                    classpath(files("${escapeString(bJar)}"))
                }
            }
            apply(from = "../some.gradle.kts")

            $repositoriesBlock

            dependencies {
                implementation(files("${escapeString(precompiledJar)}"))
            }
        """)
        def precompiled = withFile("b/src/main/kotlin/precompiled/precompiled.gradle.kts", "")
        return new BuildSpec(
            scripts: [
                settings: settings,
                root: root,
                a: a,
                b: b,
                precompiled: precompiled
            ],
            appliedScripts: [
                some: some
            ],
            jars: [
                some: someJar,
                settings: settingsJar,
                root: rootJar,
                a: aJar,
                b: bJar,
                precompiled: precompiledJar
            ]
        )
    }

    protected static void assertModelMatchesBuildSpec(KotlinDslScriptsModel model, BuildSpec spec) {

        model.scriptModels.values().each { script ->
            assertContainsGradleKotlinDslJars(script.classPath)
        }

        def settingsClassPath = canonicalClasspathOf(model, spec.scripts.settings)
        assertNotContainsBuildSrc(settingsClassPath)
        assertContainsGradleKotlinDslJars(settingsClassPath)
        assertIncludes(settingsClassPath, spec.jars.settings)
        assertExcludes(settingsClassPath, spec.jars.root, spec.jars.a, spec.jars.b)

        def rootClassPath = canonicalClasspathOf(model, spec.scripts.root)
        assertContainsBuildSrc(rootClassPath)
        assertIncludes(rootClassPath, spec.jars.root)
        assertExcludes(rootClassPath, spec.jars.a, spec.jars.b)

        def aClassPath = canonicalClasspathOf(model, spec.scripts.a)
        assertContainsBuildSrc(aClassPath)
        assertIncludes(aClassPath, spec.jars.root, spec.jars.a)
        assertExcludes(aClassPath, spec.jars.b)

        def bClassPath = canonicalClasspathOf(model, spec.scripts.b)
        assertContainsBuildSrc(bClassPath)
        assertIncludes(bClassPath, spec.jars.root, spec.jars.b)
        assertExcludes(bClassPath, spec.jars.a)

        def precompiledClassPath = canonicalClasspathOf(model, spec.scripts.precompiled)
        assertNotContainsBuildSrc(precompiledClassPath)
        assertIncludes(precompiledClassPath, spec.jars.precompiled)
        assertExcludes(precompiledClassPath, spec.jars.root, spec.jars.a, spec.jars.b)
    }

    protected static void assertModelAppliedScripts(KotlinDslScriptsModel model, BuildSpec spec) {
        spec.appliedScripts.each { appliedScript ->
            def classPath = model.scriptModels[appliedScript.value].classPath
            assertContainsGradleKotlinDslJars(classPath)
            assertContainsBuildSrc(classPath)
            def appliedJar = spec.jars[appliedScript.key]
            assertIncludes(classPath, appliedJar)
            assertExcludes(classPath, *(spec.jars.values() - appliedJar) as TestFile[])
        }
    }
}
