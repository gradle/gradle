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

import org.gradle.api.internal.GradleInternal
import org.gradle.initialization.layout.BuildLayoutConfiguration
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator
import org.gradle.internal.declarativedsl.evaluator.GradleProcessInterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.StoringInterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.defaults.DeclarativeModelDefaultsHandler
import org.gradle.internal.declarativedsl.evaluator.defaultDeclarativeScriptEvaluator
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.plugin.software.internal.ModelDefaultsHandler
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
import java.io.File


class DeclarativeDslServices : AbstractGradleModuleServices() {
    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(BuildServices)
    }
}


internal
object BuildServices : ServiceRegistrationProvider {

    @Provides
    fun createDeclarativeKotlinScriptEvaluator(
        softwareTypeRegistry: SoftwareTypeRegistry,
        gradleInternal: GradleInternal,
        buildLayoutFactory: BuildLayoutFactory
    ): DeclarativeKotlinScriptEvaluator {
        val schemaBuilder = StoringInterpretationSchemaBuilder(GradleProcessInterpretationSchemaBuilder(softwareTypeRegistry), buildLayoutFactory.settingsDir(gradleInternal))
        return defaultDeclarativeScriptEvaluator(schemaBuilder, softwareTypeRegistry)
    }

    @Provides
    fun createSoftwareTypeConventionHandler(
        softwareTypeRegistry: SoftwareTypeRegistry
    ): ModelDefaultsHandler {
        return DeclarativeModelDefaultsHandler(softwareTypeRegistry)
    }

    private
    fun BuildLayoutFactory.settingsDir(gradle: GradleInternal): File =
        getLayoutFor(BuildLayoutConfiguration(gradle.startParameter)).settingsDir
}
