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

package org.gradle.kotlin.dsl.tooling.builders.r980

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import java.lang.reflect.Proxy

import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinScriptModelParameters.setModelParameters

/**
 * From Gradle 9.8 onwards, restricting the {@link KotlinDslScriptsModel} to an explicit set of scripts via the
 * {@code org.gradle.tooling.model.kotlin.dsl.scripts} property is deprecated for removal in Gradle 10:
 * setting it still works but emits a deprecation warning.
 *
 * See {@code r60/r68 KotlinDslGivenScriptsModelCrossVersionSpec} for the warning-free behaviour of older versions.
 */
@TargetGradleVersion(">=9.8")
@LeaksFileHandles("Kotlin Compiler Daemon taking time to shut down")
class KotlinDslGivenScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    @SuppressWarnings('GrDeprecatedAPIUsage')
    private static final String SCRIPTS_GRADLE_PROPERTY_NAME = KotlinDslScriptsModel.SCRIPTS_GRADLE_PROPERTY_NAME

    def "explicit scripts property is deprecated but still honored"() {
        given:
        def spec = withSingleSubproject()
        def requestedScripts = [spec.scripts.a]
        expectDocumentedDeprecationWarning(
            "Requesting the KotlinDslScriptsModel for an explicit set of scripts using the '${SCRIPTS_GRADLE_PROPERTY_NAME}' Gradle property. " +
                "This behavior has been deprecated. This is scheduled to be removed in Gradle 10. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_9.html#kotlin_dsl_scripts_model_explicit_scripts"
        )

        when:
        def model = loadToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, false, true, requestedScripts)
        }

        then:
        model.scriptModels.keySet() == requestedScripts.collect { it.canonicalFile } as Set
    }

    // Replaces the explicit-scripts variant in r60.KotlinDslScriptsModelCrossVersionSpec that no longer runs against current Gradle.
    def "multi-scripts model is dehydrated over the wire"() {

        given:
        withBuildSrc()
        buildFileKts << ""

        when:
        def model = loadToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, true, true)
        }
        def source = Proxy.getInvocationHandler(model).sourceObject

        then:
        def commonModel = source.commonModel
        commonModel != null
        commonModel.classPath.size() > 0
        commonModel.implicitImports.size() > 0

        and:
        def scriptModels = source.dehydratedScriptModels
        scriptModels != null
        scriptModels.containsKey(buildFileKts)
    }
}
