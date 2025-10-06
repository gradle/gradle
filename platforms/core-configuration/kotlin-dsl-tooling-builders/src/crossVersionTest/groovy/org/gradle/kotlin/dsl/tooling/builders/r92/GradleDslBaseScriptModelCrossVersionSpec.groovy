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

package org.gradle.kotlin.dsl.tooling.builders.r92

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel

@TargetGradleVersion(">=9.2")
class GradleDslBaseScriptModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "GradleDslBaseScriptModel is obtained without configuring projects"() {

        when:
        def listener = new ConfigurationPhaseMonitoringListener()
        GradleDslBaseScriptModel model = withConnection { connection ->
            connection
                .model(GradleDslBaseScriptModel)
                .addProgressListener(listener)
                .get()
        }

        then:
        model != null

        and:
        def groovyModel = model.groovyDslBaseScriptModel
        groovyModel != null

        and:
        def kotlinModel = model.kotlinDslBaseScriptModel
        kotlinModel != null

        and: "Groovy DSL script compile classpath"
        groovyModel.compileClassPath.find { it.name.contains("gradle-api-") && it.name.endsWith(".jar") }
        groovyModel.compileClassPath.find { it.name.contains("groovy-") && it.name.endsWith(".jar") }
        groovyModel.compileClassPath.find { it.name.contains("kotlin-stdlib-") && it.name.endsWith(".jar") }
        groovyModel.compileClassPath.find { it.name.contains("gradle-kotlin-dsl-") && it.name.endsWith(".jar") } == null

        and: "Groovy DSL implicit imports"
        groovyModel.implicitImports.find {it.equals("org.gradle.api.Action")}
        groovyModel.implicitImports.find {it.equals("org.gradle.api.artifacts.ComponentMetadata")}
        groovyModel.implicitImports.find {it.equals("java.math.BigDecimal")}
        groovyModel.implicitImports.find {it.equals("java.io.*")}

        and: "Kotlin DSL script templates classpath"
        !kotlinModel.templateClassNames.isEmpty()
        loadClassesFrom(kotlinModel.scriptTemplatesClassPath, kotlinModel.templateClassNames)

        def legacyScriptTemplateAndResolverClassNames = Arrays.asList(
                "org.gradle.kotlin.dsl.KotlinInitScript",
                "org.gradle.kotlin.dsl.KotlinSettingsScript",
                "org.gradle.kotlin.dsl.KotlinBuildScript",
                "org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver"
        )
        loadClassesFrom(kotlinModel.scriptTemplatesClassPath, legacyScriptTemplateAndResolverClassNames)

        and: "Kotlin DSL script compile classpath"
        !kotlinModel.compileClassPath.isEmpty()
        kotlinModel.compileClassPath.find { it.name.contains("gradle-api-") && it.name.endsWith(".jar") }
        kotlinModel.compileClassPath.find { it.name.contains("groovy-") && it.name.endsWith(".jar") }
        kotlinModel.compileClassPath.find { it.name.contains("kotlin-stdlib-") && it.name.endsWith(".jar") }
        kotlinModel.compileClassPath.find { it.name.contains("gradle-kotlin-dsl-") && it.name.endsWith(".jar") }

        and: "Kotlin DSL script implicit imports"
        kotlinModel.implicitImports.find {it.equals("org.gradle.api.Action")}
        kotlinModel.implicitImports.find {it.equals("org.gradle.api.artifacts.ComponentMetadata")}
        kotlinModel.implicitImports.find {it.equals("org.gradle.kotlin.dsl.*")}
        kotlinModel.implicitImports.find {it.equals("java.math.BigDecimal")}
        kotlinModel.implicitImports.find {it.equals("java.io.File")}

        and: "no configuration"
        listener.hasSeenSomeEvents && listener.configPhaseStartEvents.isEmpty()
    }

    private static void loadClassesFrom(List<File> classPath, List<String> classNames) {
        classLoaderFor(classPath).withCloseable { loader ->
            classNames.each {
                assert loader.loadClass(it) != null
            }
        }
    }

    private static URLClassLoader classLoaderFor(List<File> classPath) {
        new URLClassLoader(
                classPath.collect { it.toURI().toURL() } as URL[],
                (ClassLoader) null // we don't want the classloader to have a fallback
        )
    }
}
