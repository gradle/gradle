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

package org.gradle.kotlin.dsl.support

import org.gradle.api.JavaVersion
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.deprecation.DeprecationLogger
import java.io.Serializable


data class KotlinCompilerOptions(
    val jvmTarget: JavaVersion = JavaVersion.current(),
    val allWarningsAsErrors: Boolean = false,
    val skipMetadataVersionCheck: Boolean = true,
) : Serializable


fun kotlinCompilerOptions(gradleProperties: GradlePropertiesController): KotlinCompilerOptions =
    KotlinCompilerOptions(
        allWarningsAsErrors = getCompilerOptionBoolean(gradleProperties, allWarningsAsErrorsPropertyName, false),
        skipMetadataVersionCheck = getCompilerOptionBoolean(gradleProperties, skipMetadataVersionCheckPropertyName, true) {
            nagAboutUnsetSkipMetadataVersionCheckProperty()
        }
    )


private
val allWarningsAsErrorsPropertyName = "org.gradle.kotlin.dsl.allWarningsAsErrors"


private
val skipMetadataVersionCheckPropertyName = "org.gradle.kotlin.dsl.skipMetadataVersionCheck"


/**
 * Read property value for compiler options.
 *
 * Kotlin compiler options for scripts can be set either via a System property or a Gradle property.
 * System properties have precedence, same as in `LayoutToPropertiesConverter`.
 */
private
fun getCompilerOptionBoolean(gradleProperties: GradlePropertiesController, propertyName: String, defaultValue: Boolean, whenUnset: (() -> Unit)? = null): Boolean {
    val systemProp = System.getProperty(propertyName)
    val gradleProp = gradleProperties.gradleProperties.find(propertyName)
    return when {
        // System properties have precedence, same as in LayoutToPropertiesConverter
        systemProp != null -> systemProp == "true"
        gradleProp != null -> gradleProp == "true"
        else -> defaultValue.also { whenUnset?.invoke() }
    }
}


private
fun nagAboutUnsetSkipMetadataVersionCheckProperty() {
    DeprecationLogger.deprecateBuildInvocationFeature("Skipping Kotlin metadata version check in Kotlin DSL script compilation")
        .withContext("Skipping the check may lead to hard to troubleshoot errors when libraries built with Kotlin versions unsupported by the Kotlin embedded in Gradle are used in build logic.")
        .withAdvice("To opt in to the future behaviour, set the '$skipMetadataVersionCheckPropertyName' System property to `false`.")
        .startingWithGradle9("the Kotlin metadata version check will be enabled by default")
        .withUpgradeGuideSection(8, "kotlin_skip_metadata_version_check")
        .nagUser()
}
