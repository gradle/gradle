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

package org.gradle.kotlin.dsl.tooling.builders.r60

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.kotlin.dsl.tooling.builders.KotlinDslScriptsModelClient
import org.gradle.kotlin.dsl.tooling.builders.KotlinDslScriptsModelRequest
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import java.lang.reflect.Proxy

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.hasItem
import static org.junit.Assert.assertThat


@TargetGradleVersion(">=6.0")
@LeaksFileHandles("Kotlin Compiler Daemon taking time to shut down")
class KotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "can fetch model for the scripts of a build"() {

        given:
        def spec = withMultiProjectBuildWithBuildSrc()

        when:
        def model = kotlinDslScriptsModelFor()

        then:
        model.scriptModels.keySet() == spec.scripts.values() as Set

        and:
        assertModelMatchesBuildSpec(model, spec)
    }

    def "can fetch model for the scripts of a build in lenient mode"() {

        given:
        def spec = withMultiProjectBuildWithBuildSrc()

        and:
        spec.scripts.a << """
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
            spec.scripts.a,
            "Unresolved reference: script_body_compilation_error"
        )
    }

    def "can fetch model for a given set of scripts"() {

        given:
        def spec = withMultiProjectBuildWithBuildSrc()
        def requestedScripts = spec.scripts.values() + spec.appliedScripts.some

        when:
        def model = kotlinDslScriptsModelFor(requestedScripts)

        then:
        model.scriptModels.keySet() == requestedScripts as Set

        and:
        assertModelMatchesBuildSpec(model, spec)

        and:
        assertModelAppliedScripts(model, spec)
    }

    def "can fetch model for a given set of scripts of a build in lenient mode"() {

        given:
        def spec = withMultiProjectBuildWithBuildSrc()
        def requestedScripts = spec.scripts.values() + spec.appliedScripts.some

        and:
        spec.scripts.a << """
            script_body_compilation_error
        """

        when:
        def model = kotlinDslScriptsModelFor(true, requestedScripts)

        then:
        model.scriptModels.keySet() == requestedScripts as Set

        and:
        assertModelMatchesBuildSpec(model, spec)

        and:
        assertModelAppliedScripts(model, spec)

        and:
        assertHasExceptionMessage(
            model,
            spec.scripts.a,
            "Unresolved reference: script_body_compilation_error"
        )
    }

    def "single request models equal multi requests models"() {

        given:
        toolingApi.requireIsolatedUserHome()

        and:
        def spec = withMultiProjectBuildWithBuildSrc()

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

    def "multi-scripts model is dehydrated over the wire"() {

        given:
        withBuildSrc()
        buildFileKts << ""

        when:
        def model = kotlinDslScriptsModelFor(buildFileKts)

        then:
        def source = Proxy.getInvocationHandler(model).sourceObject
        source.scripts == [buildFileKts]

        and:
        def commonModel = source.commonModel
        commonModel != null
        commonModel.classPath.size() > 0
        commonModel.classPath.find { it.name == "buildSrc.jar" }
        commonModel.sourcePath.size() > 0
        commonModel.implicitImports.size() > 0

        and:
        def scriptModels = source.dehydratedScriptModels
        scriptModels != null
        scriptModels.size() == 1

        and:
        def buildFileKtsModel = source.dehydratedScriptModels.get(buildFileKts)
        buildFileKtsModel != null
        buildFileKtsModel.classPath.isEmpty()
        buildFileKtsModel.sourcePath.isEmpty()
        buildFileKtsModel.implicitImports.isEmpty()
        buildFileKtsModel.editorReports.isEmpty()
        buildFileKtsModel.exceptions.isEmpty()
    }

    private KotlinDslScriptsModel kotlinDslScriptsModelFor(boolean lenient = false, File... scripts) {
        return kotlinDslScriptsModelFor(lenient, scripts.toList())
    }

    private KotlinDslScriptsModel kotlinDslScriptsModelFor(boolean lenient = false, Iterable<File> scripts) {
        return withConnection { connection ->
            new KotlinDslScriptsModelClient().fetchKotlinDslScriptsModel(
                connection,
                new KotlinDslScriptsModelRequest(
                    scripts.toList(),
                    null, null, [], [], lenient
                )
            )
        }
    }

    private static List<File> canonicalClasspathOf(KotlinDslScriptsModel model, File script) {
        return model.scriptModels[script].classPath.collect { it.canonicalFile }
    }

    private static class BuildSpec {
        Map<String, TestFile> scripts
        Map<String, TestFile> appliedScripts
        Map<String, TestFile> jars
    }

    private BuildSpec withMultiProjectBuildWithBuildSrc() {
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

    private static void assertModelMatchesBuildSpec(KotlinDslScriptsModel model, BuildSpec spec) {

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

    private static void assertModelAppliedScripts(KotlinDslScriptsModel model, BuildSpec spec) {
        spec.appliedScripts.each { appliedScript ->
            def classPath = model.scriptModels[appliedScript.value].classPath
            assertContainsGradleKotlinDslJars(classPath)
            assertContainsBuildSrc(classPath)
            def appliedJar = spec.jars[appliedScript.key]
            assertIncludes(classPath, appliedJar)
            assertExcludes(classPath, *(spec.jars.values() - appliedJar) as TestFile[])
        }
    }

    private static void assertHasExceptionMessage(KotlinDslScriptsModel model, TestFile script, String message) {
        assertThat(model.scriptModels[script].exceptions, hasItem(containsString(message)))
    }
}
