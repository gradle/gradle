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
import groovy.transform.TupleConstructor
import org.gradle.util.GradleVersion

import java.util.function.Consumer

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations {

    @TupleConstructor
    private static class IsPropertyInfo {
        String name
        String existing
        String replacement
        String location
    }

    private static final List<IsPropertyInfo> IS_PROPERTIES = [
        new IsPropertyInfo("crunchPngs", "isCrunchPngs", "getCrunchPngs", "com.android.build.gradle.internal.dsl.BuildType\$AgpDecorated"),
        new IsPropertyInfo("useProguard", "isUseProguard", "getUseProguard", "com.android.build.gradle.internal.dsl.BuildType"),
        new IsPropertyInfo("wearAppUnbundled", "isWearAppUnbundled", "getWearAppUnbundled", "com.android.build.api.variant.impl.ApplicationVariantImpl"),
    ]

    private void expectIsPropertyDeprecationWarningsUsing(Consumer<String> deprecationFunction) {
        for (def prop : IS_PROPERTIES) {
            deprecationFunction.accept(
                "Declaring '${prop.name}' as a property using an 'is-' method with a Boolean type on ${prop.location} has been deprecated. " +
                    "Starting with Gradle 10.0, this property will no longer be treated like a property. " +
                    "The combination of method name and return type is not consistent with Java Bean property rules. " +
                    "Add a method named '${prop.replacement}' with the same behavior and mark the old one with @Deprecated, or change the type of '${prop.location}.${prop.existing}' (and the setter) to 'boolean'. " +
                    "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#groovy_boolean_properties")
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
