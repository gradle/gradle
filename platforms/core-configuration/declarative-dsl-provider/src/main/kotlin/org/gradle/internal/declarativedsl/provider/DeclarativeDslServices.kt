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
import org.gradle.api.model.ObjectFactory
import org.gradle.initialization.layout.BuildLayoutConfiguration
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator
import org.gradle.internal.declarativedsl.evaluator.GradleProcessInterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.MemoizedInterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.StoringInterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.defaults.DeclarativeModelDefaultsHandler
import org.gradle.internal.declarativedsl.evaluator.defaultDeclarativeScriptEvaluator
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.event.ListenerManager
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

    fun configure(listenerManager: ListenerManager, serviceRegistration: ServiceRegistration){
        serviceRegistration.add(SettingsUnderInitialization::class.java, SettingsUnderInitialization(listenerManager))
    }

    @Provides
    fun createDeclarativeKotlinScriptEvaluator(
        softwareTypeRegistry: SoftwareTypeRegistry,
        schemaBuilder: InterpretationSchemaBuilder
    ): DeclarativeKotlinScriptEvaluator {
        return defaultDeclarativeScriptEvaluator(schemaBuilder, softwareTypeRegistry)
    }

    @Provides
    fun createInterpretationSchemaBuilder(
        softwareTypeRegistry: SoftwareTypeRegistry,
        buildLayoutFactory: BuildLayoutFactory,
        settingsUnderInitialization: SettingsUnderInitialization,
        gradleInternal: GradleInternal
    ) = MemoizedInterpretationSchemaBuilder(
        StoringInterpretationSchemaBuilder(GradleProcessInterpretationSchemaBuilder(settingsUnderInitialization::instance, softwareTypeRegistry), buildLayoutFactory.settingsDir(gradleInternal))
    )

    @Provides
    fun createDeclarativeModelDefaultsHandler(
        softwareTypeRegistry: SoftwareTypeRegistry,
        objectFactory: ObjectFactory
    ): ModelDefaultsHandler {
        return objectFactory.newInstance(DeclarativeModelDefaultsHandler::class.java, softwareTypeRegistry)
    }

    private
    fun BuildLayoutFactory.settingsDir(gradle: GradleInternal): File =
        getLayoutFor(BuildLayoutConfiguration(gradle.startParameter)).settingsDir
}
