/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.provider

import org.gradle.internal.declarativedsl.evaluator.DefaultDeclarativeKotlinScriptEvaluator
import org.gradle.internal.declarativedsl.evaluator.DefaultInterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator
import org.gradle.internal.declarativedsl.evaluator.StoringInterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.model.annotations.RestrictedAnnotationHandler
import org.gradle.internal.declarativedsl.model.annotations.NestedRestrictedAnnotationHandler
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


class DeclarativeDslServiceRegistry : AbstractPluginServiceRegistry() {
    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.addProvider(GlobalServices)
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(BuildServices)
    }
}


internal
object GlobalServices {
    @Suppress("unused")
    fun createRestrictedAnnotationHandler(): RestrictedAnnotationHandler {
        return RestrictedAnnotationHandler()
    }

    fun createRestrictedNestedAnnotationHandler(): NestedRestrictedAnnotationHandler {
        return NestedRestrictedAnnotationHandler()
    }
}


internal
object BuildServices {
    @Suppress("unused")
    fun createDeclarativeKotlinScriptEvaluator(softwareTypeRegistry: SoftwareTypeRegistry): DeclarativeKotlinScriptEvaluator {
        val schemaBuilder = StoringInterpretationSchemaBuilder(DefaultInterpretationSchemaBuilder(softwareTypeRegistry))
        return DefaultDeclarativeKotlinScriptEvaluator(schemaBuilder)
    }
}
