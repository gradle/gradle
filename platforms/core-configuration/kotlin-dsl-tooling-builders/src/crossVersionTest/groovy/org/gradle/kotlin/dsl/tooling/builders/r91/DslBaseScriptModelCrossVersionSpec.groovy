/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders.r91

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.tooling.model.kotlin.dsl.DslBaseScriptModel

import java.util.stream.Collectors

@TargetGradleVersion(">=9.1")
class DslBaseScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "DslBaseScriptModel is obtained without configuring projects"() {

        when:
        def listener = new ConfigurationPhaseMonitoringListener()
        DslBaseScriptModel model = withConnection { connection ->
            connection
                .model(DslBaseScriptModel)
                .addProgressListener(listener)
                .get()
        }

        then:
        model != null

        and: "script templates classpath"
        println "model.scriptTemplatesClassPath = ${model.scriptTemplatesClassPath.stream().limit(10).map {it.name }.collect(Collectors.joining("\n\t", "\n\t", ""))}" // TODO: remove
        loadClassesFrom(
            model.scriptTemplatesClassPath,
            // Script templates for IDE support
            "org.gradle.kotlin.dsl.KotlinGradleScriptTemplate",
            "org.gradle.kotlin.dsl.KotlinSettingsScriptTemplate",
            "org.gradle.kotlin.dsl.KotlinProjectScriptTemplate",
            // Legacy script templates for IDE support
            "org.gradle.kotlin.dsl.KotlinInitScript",
            "org.gradle.kotlin.dsl.KotlinSettingsScript",
            "org.gradle.kotlin.dsl.KotlinBuildScript",
            // Legacy script dependencies resolver for IDE support
            "org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver"
        )

        and: "implicit imports"
        !model.implicitImports.isEmpty()
        println "model.implicitImports = ${model.implicitImports.stream().limit(10).collect(Collectors.joining("\n\t", "\n\t", ""))}" // TODO: remove

        and: "base classpath"
        !model.kotlinDslClassPath.isEmpty()
        println "model.kotlinDslClassPath = ${model.kotlinDslClassPath.stream().limit(10).map {it.name }.collect(Collectors.joining("\n\t", "\n\t", ""))}" // TODO: remove
        model.kotlinDslClassPath.find { it.name.contains("gradle-api-") && it.name.endsWith(".jar") }
        model.kotlinDslClassPath.find { it.name.contains("groovy-") && it.name.endsWith(".jar") }
        model.kotlinDslClassPath.find { it.name.contains("gradle-kotlin-dsl-") && it.name.endsWith(".jar") }

        and: "no configuration"
        listener.hasSeenSomeEvents && listener.configPhaseStartEvents.isEmpty()
    }

    private static void loadClassesFrom(List<File> classPath, String... classNames) {
        classLoaderFor(classPath).withCloseable { loader ->
            classNames.each {
                loader.loadClass(it)
            }
        }
    }

    private static URLClassLoader classLoaderFor(List<File> classPath) {
        new URLClassLoader(classPath.collect { it.toURI().toURL() } as URL[])
    }
}
