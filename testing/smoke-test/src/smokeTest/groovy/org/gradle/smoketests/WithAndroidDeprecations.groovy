/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.smoketests

import groovy.transform.SelfType
import org.gradle.internal.Pair
import org.gradle.util.GradleVersion

import java.util.function.Consumer

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations {

    private static final List<Pair<String, String>> IS_PROPERTIES = [
        Pair.of("com.android.build.gradle.internal.dsl.BuildType\$AgpDecorated.isCrunchPngs", "getCrunchPngs"),
        Pair.of("com.android.build.gradle.internal.dsl.BuildType.isUseProguard", "getUseProguard"),
        Pair.of("com.android.build.api.variant.impl.ApplicationVariantImpl.isWearAppUnbundled", "getWearAppUnbundled"),
    ]

    private void expectIsPropertyDeprecationWarningsUsing(Consumer<String> deprecationFunction) {
        for (def prop : IS_PROPERTIES) {
            def existing = prop.left
            def replacement = prop.right

            deprecationFunction.accept("Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. Add a method named '${replacement}' with the same behavior and mark the old one with @Deprecated, or change the type of '${existing}' (and the setter) to 'boolean'. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#groovy_boolean_properties")
        }
    }

    void expectIsPropertyDeprecationWarnings() {
        expectIsPropertyDeprecationWarningsUsing { message ->
            runner.expectDeprecationWarning(message, "https://issuetracker.google.com/issues/370546370")
        }
    }

    void maybeExpectIsPropertyDeprecationWarnings() {
        expectIsPropertyDeprecationWarningsUsing { message ->
            runner.maybeExpectLegacyDeprecationWarning(message)
        }
    }

}
