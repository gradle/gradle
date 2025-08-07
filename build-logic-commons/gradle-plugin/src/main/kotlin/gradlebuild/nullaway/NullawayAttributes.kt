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

package gradlebuild.nullaway

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.provider.Provider

enum class NullawayState {
    ENABLED, DISABLED
}

object NullawayAttributes {
    val nullawayAttribute = Attribute.of("org.gradle.gradlebuild.nullaway", NullawayState::class.java)

    fun addToConfiguration(configuration: NamedDomainObjectProvider<Configuration>, value: Provider<NullawayState>) {
        configuration.configure {
            attributes {
                attributeProvider(nullawayAttribute, value)
            }
        }
    }
}

class NullawayCompatibilityRule : AttributeCompatibilityRule<NullawayState> {
    override fun execute(details: CompatibilityCheckDetails<NullawayState>) {
        with(details) {
            when {
                // Nullaway-enabled targets must not depend on nullaway-disabled ones.
                // They can depend on nullaway-undefined, which any external dependency is going to be.
                producerValue == NullawayState.DISABLED && consumerValue == NullawayState.ENABLED -> incompatible()
                else -> compatible()
            }
        }
    }
}
