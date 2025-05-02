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
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.gradle.util.GradleVersion

import java.lang.reflect.Proxy

import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinScriptModelParameters.setModelParameters

@TargetGradleVersion(">=6.0")
@LeaksFileHandles("Kotlin Compiler Daemon taking time to shut down")
@Flaky(because = 'https://github.com/gradle/gradle-private/issues/3414')
class KotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinDslScriptsModelCrossVersionSpec {

    def "single request models equal multi requests models"() {

        given:
        toolingApi.requireIsolatedUserHome()

        and:
        maybeExpectAccessorsDeprecation()
        def spec = withMultiProjectBuildWithBuildSrc()

        when:
        def model = loadToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, false, true, [])
        }

        Map<File, KotlinDslScriptModel> singleRequestModels = model.scriptModels

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
        def model = loadToolingModel(KotlinDslScriptsModel) {
            setModelParameters(it, true, true, [buildFileKts])
        }
        def source = Proxy.getInvocationHandler(model).sourceObject

        then:
        if (targetVersion.baseVersion < GradleVersion.version("8.6")) {
            assert source.scripts == [buildFileKts]
        }

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

    void maybeExpectAccessorsDeprecation() {
        if (targetVersion >= GradleVersion.version("7.6") && targetVersion < GradleVersion.version("8.0")) {
            expectDocumentedDeprecationWarning("Non-strict accessors generation for Kotlin DSL precompiled script plugins has been deprecated. This will change in Gradle 8.0. Strict accessor generation will become the default. To opt in to the strict behavior, set the 'org.gradle.kotlin.dsl.precompiled.accessors.strict' system property to `true`. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#strict-kotlin-dsl-precompiled-scripts-accessors")
        }
    }
}
