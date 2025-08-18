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
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptTemplateModel
import org.gradle.util.GradleVersion

@TargetGradleVersion(">=9.1")
class KotlinBuildScriptTemplateModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "KotlinBuildScriptTemplateModel is obtained without configuring projects"() {

        if (targetVersion.baseVersion >= GradleVersion.version("9.1")) {
            expectDocumentedDeprecationWarning(
                "The org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptTemplateModel type has been deprecated. " +
                    "This will fail with an error in Gradle 10. " +
                    "Please use the org.gradle.tooling.model.dsl.DslBaseScriptModel type instead."
            )
        }

        when:
        def listener = new ConfigurationPhaseMonitoringListener()
        KotlinBuildScriptTemplateModel model = withConnection { connection ->
            connection
                .model(KotlinBuildScriptTemplateModel)
                .addProgressListener(listener)
                .get()
        }

        then:
        model != null
        listener.hasSeenSomeEvents && listener.configPhaseStartEvents.isEmpty()
    }
}
