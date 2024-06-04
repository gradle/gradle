/*
 * Copyright 2024 the original author or authors.
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

package gradlebuild

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.attributes.Attribute


val SLF4J_ATTRIBUTE = Attribute.of("org.gradle.slf4j", String::class.java)


// Must be top-level class (or static or not abstract) or else:
// > Could not create an instance of type Build_gradle$HasBindingAttributeRule.
//            > Class Build_gradle.HasBindingAttributeRule is a non-static inner class.
@CacheableRule
abstract class HasBindingAttributeRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants() {
            attributes {
                attribute(SLF4J_ATTRIBUTE, "has-binding")
            }
        }
    }
}
