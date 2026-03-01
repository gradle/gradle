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

package org.gradle.kotlin.dsl.tooling.builders.r93

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.util.GradleVersion

import static org.junit.Assume.assumeTrue

@TargetGradleVersion(">=9.3")
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
        println "=========================="
        for (final def item in kotlinModel.scriptTemplatesClassPath) {
            println item
        }
        println "=========================="
        loadClassesFrom(kotlinModel.scriptTemplatesClassPath, ["org.gradle.api.HasImplicitReceiver"])
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

    def "GradleDslBaseScriptModel can be obtained even after a settings #typeOfFailure failure with #apiType"() {
        assumeApiSupportedOnVersion(apiType)

        given:
        settingsFileKts << error

        when:
        def result = succeeds {
            action(new FetchBaseModelAfterSettingsEvaluationAction(apiType))
                .run()
        }

        then:
        println(result.errorsBeforeBaseModel[0])
        result.errorsBeforeBaseModel.size() == 1
        result.errorsBeforeBaseModel[0] =~ expectedReportedError
        result.baseModel != null
        result.baseModel.groovyDslBaseScriptModel != null
        result.baseModel.kotlinDslBaseScriptModel != null

        where:
        typeOfFailure | error                                    | expectedReportedError                            | apiType
        "compilation" | "broken !!!"                             | /Script compilation error:\s+Line 1: broken !!!/ | ApiType.FETCH
        "runtime"     | "throw RuntimeException(\"broken !!!\")" | /Settings file '.*?' line: 1\s+broken !!!/       | ApiType.FETCH
        "compilation" | "broken !!!"                             | /Script compilation error:\s+Line 1: broken !!!/ | ApiType.GET_MODEL
        "runtime"     | "throw RuntimeException(\"broken !!!\")" | /Settings file '.*?' line: 1\s+broken !!!/       | ApiType.GET_MODEL
    }

    def "GradleDslBaseScriptModel can be obtained even after a project #typeOfFailure failure with #apiType"() {
        assumeApiSupportedOnVersion(apiType)

        given:
        buildFileKts << error

        when:
        def result = succeeds {
            action(new FetchBaseModelAfterProjectConfigurationAction(apiType))
                .run()
        }

        then:
        result.errorsBeforeBaseModel.size() == 1
        result.errorsBeforeBaseModel[0].contains(expectedReportedError)
        result.baseModel != null
        result.baseModel.groovyDslBaseScriptModel != null
        result.baseModel.kotlinDslBaseScriptModel != null

        where:
        typeOfFailure | error                                    | expectedReportedError                         | apiType
        "compilation" | "broken !!!"                             | "A problem occurred configuring root project" | ApiType.FETCH
        "runtime"     | "throw RuntimeException(\"broken !!!\")" | "A problem occurred configuring root project" | ApiType.FETCH
        "compilation" | "broken !!!"                             | "A problem occurred configuring root project" | ApiType.GET_MODEL
        "runtime"     | "throw RuntimeException(\"broken !!!\")" | "A problem occurred configuring root project" | ApiType.GET_MODEL
    }

    static class FetchBaseModelAfterSettingsEvaluationAction implements BuildAction<FetchBaseModelLastActionResult>, Serializable {

        final ApiType apiType

        FetchBaseModelAfterSettingsEvaluationAction(ApiType apiType) {
            this.apiType = apiType;
        }

        @Override
        FetchBaseModelLastActionResult execute(BuildController controller) {
            return fetchBaseModelAfter(controller, GradleBuild, apiType)
        }
    }

    static class FetchBaseModelAfterProjectConfigurationAction implements BuildAction<FetchBaseModelLastActionResult>, Serializable {

        final ApiType apiType

        FetchBaseModelAfterProjectConfigurationAction(ApiType apiType) {
            this.apiType = apiType;
        }

        @Override
        FetchBaseModelLastActionResult execute(BuildController controller) {
            return fetchBaseModelAfter(controller, KotlinDslScriptsModel, apiType)
        }
    }

    private static FetchBaseModelLastActionResult fetchBaseModelAfter(BuildController controller, Class<?> modelType, ApiType apiType) {
        def model
        List<String> failures = []
        if (apiType == ApiType.FETCH) {
            def result = controller.fetch(modelType)
            failures = result.failures.collect { it.message }
            model = controller.fetch(GradleDslBaseScriptModel).model
        } else {
            try {
                controller.getModel(modelType)
            } catch (Exception e) {
                failures.add(e.message)
            }
            model = controller.getModel(GradleDslBaseScriptModel)
        }
        return new FetchBaseModelLastActionResult(failures, model)
    }

    static class FetchBaseModelLastActionResult implements Serializable {
        final List<String> errorsBeforeBaseModel
        final GradleDslBaseScriptModel baseModel

        FetchBaseModelLastActionResult(List<String> errorsBeforeBaseModel, GradleDslBaseScriptModel baseModel) {
            this.errorsBeforeBaseModel = errorsBeforeBaseModel
            this.baseModel = baseModel
        }
    }

    enum ApiType {
        FETCH,
        GET_MODEL
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

    private assumeApiSupportedOnVersion(ApiType apiType) {
        assumeTrue("Fetch requires Tooling API >= 9.3.0", apiType == ApiType.GET_MODEL
            || apiType == ApiType.FETCH && toolingApi.toolingApiVersion >= GradleVersion.version("9.3.0"))
    }
}
