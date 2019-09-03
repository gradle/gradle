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
import org.gradle.kotlin.dsl.resolver.KotlinDslScriptsModelClient
import org.gradle.kotlin.dsl.resolver.KotlinDslScriptsModelRequest
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.kotlin.dsl.tooling.models.KotlinDslScriptsModel

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString


@TargetGradleVersion(">=6.0")
class KotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "can fetch model for a set of scripts"() {

        given:
        withBuildSrc()
        def rootJar = withEmptyJar("classes_root.jar").canonicalFile
        def aJar = withEmptyJar("classes_a.jar").canonicalFile
        def bJar = withEmptyJar("classes_b.jar").canonicalFile

        and:
        def settings = withSettings("""
            include("a", "b")
        """)
        def root = withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("${escapeString(rootJar)}"))
                }
            }
        """)
        def a = withBuildScriptIn("a", """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(aJar)}"))
                }
            }
        """)
        def b = withBuildScriptIn("b", """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(bJar)}"))
                }
            }
        """)
        def scriptFiles = [settings, root, a, b]

        when:
        def model = kotlinDslScriptsModelFor(*scriptFiles)

        then:
        model.scriptModels.size() == 4

        and:
        def settingsClassPath = model.scriptModels[settings].classPath.collect { it.name }
        settingsClassPath.contains("buildSrc.jar") // TODO invert
        !settingsClassPath.contains(rootJar.name)
        !settingsClassPath.contains(aJar.name)
        !settingsClassPath.contains(bJar.name)

        and:
        def rootClassPath = model.scriptModels[root].classPath.collect { it.name }
        rootClassPath.contains("buildSrc.jar")
        rootClassPath.contains(rootJar.name)
        !rootClassPath.contains(aJar.name)
        !rootClassPath.contains(bJar.name)

        and:
        def aClassPath = model.scriptModels[a].classPath.collect { it.name }
        aClassPath.contains("buildSrc.jar")
        aClassPath.contains(rootJar.name)
        aClassPath.contains(aJar.name)
        !aClassPath.contains(bJar.name)

        and:
        def bClassPath = model.scriptModels[b].classPath.collect { it.name }
        bClassPath.contains("buildSrc.jar")
        bClassPath.contains(rootJar.name)
        !bClassPath.contains(aJar.name)
        bClassPath.contains(bJar.name)
    }

    private KotlinDslScriptsModel kotlinDslScriptsModelFor(File... scripts) {
        return withConnection { connection ->
            KotlinDslScriptsModelClient.fetchKotlinDslScriptsModel(
                connection,
                new KotlinDslScriptsModelRequest(scripts.toList())
            )
        }
    }
}
