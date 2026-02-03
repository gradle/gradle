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

package org.gradle.internal.declarativedsl

import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.dependencycollectors.dependencyCollectors
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.KClass

class DependencyCollectorsComponentTest {
    @Test
    fun `dependency collectors are imported only as adding functions`() {
        listOf( // the same definition in Java and Kotlin
            TestReceiver::class,
            DependencyCollectorsComponentTestJava.TestReceiver::class,
        ).forEach { receiverClass ->
            schemaFrom(receiverClass).analysisSchema.run {
                val type = typeFor(receiverClass.java)
                val functions = type.memberFunctions

                Assert.assertTrue(
                    "getters like getApi, getImplementation are not imported from the class",
                    functions.map { it.simpleName }.intersect(setOf("getApi", "getImplementation")).isEmpty(),
                )
                Assert.assertTrue(
                    "no DCL properties are imported from the dependency collector properties",
                    type.properties.isEmpty()
                )
                Assert.assertEquals(
                    "only project value factory and the two dependency collectors are imported from the class",
                    setOf("project", "api", "implementation"),
                    functions.map { it.simpleName }.toSet()
                )
                Assert.assertTrue(
                    "all dependency collector-produced functions have the adding semantics",
                    functions.filter { it.simpleName == "api" || it.simpleName == "implementation" }.all { it.semantics is FunctionSemantics.AddAndConfigure }
                )
            }
        }
    }

    private fun schemaFrom(topLevelReceiverClass: KClass<*>) =
        buildEvaluationSchema(
            topLevelReceiverClass,
            analyzeEverything,
            schemaComponents = {
                gradleDslGeneralSchema()
                dependencyCollectors()
            }
        )
}

@Suppress("unused")
private interface TestReceiver : Dependencies {
    val api: DependencyCollector
    val implementation: DependencyCollector
}
