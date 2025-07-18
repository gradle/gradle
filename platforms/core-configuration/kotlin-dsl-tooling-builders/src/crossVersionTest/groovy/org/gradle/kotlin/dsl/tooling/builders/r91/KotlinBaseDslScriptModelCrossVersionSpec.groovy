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
import org.gradle.tooling.model.kotlin.dsl.KotlinBaseDslScriptModel

@TargetGradleVersion(">=9.1")
class KotlinBaseDslScriptModelCrossVersionSpec  extends AbstractKotlinScriptModelCrossVersionTest {

    def "KotlinBaseDslScriptModel is obtained without configuring projects"() {

        when:
        def listener = new ConfigurationPhaseMonitoringListener()
        KotlinBaseDslScriptModel model = withConnection { connection ->
            connection
                .model(KotlinBaseDslScriptModel)
                .addProgressListener(listener)
                .get()
        }

        then:
        model != null
        !model.implicitImports.isEmpty()
        !model.kotlinDslClassPath.isEmpty()
        model.kotlinDslClassPath.find { it.name.contains("gradle-api-") && it.name.endsWith(".jar") }
        model.kotlinDslClassPath.find { it.name.contains("groovy-") && it.name.endsWith(".jar") }
        model.kotlinDslClassPath.find { it.name.contains("gradle-kotlin-dsl-") && it.name.endsWith(".jar") }
        listener.hasSeenSomeEvents && listener.configPhaseStartEvents.isEmpty()
    }
}
