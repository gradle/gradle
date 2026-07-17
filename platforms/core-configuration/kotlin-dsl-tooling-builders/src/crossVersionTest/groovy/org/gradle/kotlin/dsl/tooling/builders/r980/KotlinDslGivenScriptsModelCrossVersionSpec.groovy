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
import org.gradle.tooling.BuildException
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import java.lang.reflect.Proxy

import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinScriptModelParameters.SCRIPTS_GRADLE_PROPERTY_NAME
import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinScriptModelParameters.setModelParameters

/**
 * From Gradle 9.8 onwards, the Isolated Projects safe builder is the default for {@link KotlinDslScriptsModel}.
 * It always builds the model for all the Kotlin DSL scripts of the build and rejects the
 * {@code org.gradle.tooling.model.kotlin.dsl.scripts} property (removed from the public API) that used to
 * restrict the requested scripts.
 * The legacy builder can be temporarily restored with the {@code org.gradle.internal.legacy-kotlin-dsl-scripts-model}
 * system property.
 *
 * See {@code r60/r68 KotlinDslGivenScriptsModelCrossVersionSpec} for the previous behaviour.
 */
@TargetGradleVersion(">=9.8")
@LeaksFileHandles("Kotlin Compiler Daemon taking time to shut down")
class KotlinDslGivenScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    private static final String LEGACY_BUILDER_FLAG = "-Dorg.gradle.internal.legacy-kotlin-dsl-scripts-model=true"

    def "explicit scripts property is rejected by default"() {
        given:
        def spec = withSingleSubproject()
        def requestedScripts = [spec.scripts.a]

        when:
        fails { connection ->
            def modelBuilder = connection.model(KotlinDslScriptsModel)
            setModelParameters(modelBuilder, false, true, requestedScripts)
            modelBuilder.get()
        }

        then:
        def e = thrown(BuildException)
        collectCauseMessages(e).any { it?.contains("Property ${SCRIPTS_GRADLE_PROPERTY_NAME} is no longer supported") }
    }

    def "explicit scripts property is honored when the legacy builder is restored via the internal flag"() {
        given:
        def spec = withSingleSubproject()
        def requestedScripts = [spec.scripts.a]

        when:
        def model = loadToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, false, true, requestedScripts)
            it.addArguments(LEGACY_BUILDER_FLAG)
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
