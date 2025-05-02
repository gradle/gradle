/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.precompile

import org.gradle.api.initialization.Settings
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.fileOperationsFor


/**
 * Legacy script template definition for precompiled Kotlin script targeting [Settings] instances.
 *
 * @see PrecompiledProjectScript
 */
@Deprecated("Kept for compatibility with precompiled script plugins published with Gradle versions prior to 6.0")
open class PrecompiledSettingsScript(target: Settings) : @Suppress("deprecation") SettingsScriptApi(target) {

    init {
        DeprecationLogger.deprecateBehaviour("Applying a Kotlin DSL precompiled script plugin published with Gradle versions < 6.0.")
            .withAdvice("Use a version of the plugin published with Gradle >= 6.0.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "kotlin_dsl_precompiled_gradle_lt_6")
            .nagUser()
    }

    override val fileOperations by lazy { fileOperationsFor(delegate) }
}
