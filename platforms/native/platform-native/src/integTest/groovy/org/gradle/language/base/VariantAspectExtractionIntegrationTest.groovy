/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class VariantAspectExtractionIntegrationTest extends AbstractIntegrationSpec {
    def "variant annotation on property with illegal type raises error"() {
        buildFile << """
        @Managed
        interface SampleBinary extends BinarySpec {
            @Variant
            Integer getVariantProp()
            void setVariantProp(Integer variant)
        }
        class Rules extends RuleSource {
            @ComponentType
            void register(TypeBuilder<SampleBinary> builder) {}
        }
        apply plugin: Rules
        """

        expect:
        fails "components"
        failure.assertHasCause "Invalid managed model type SampleBinary: @Variant annotation only allowed for properties of type String and org.gradle.api.Named, but property has type java.lang.Integer (invalid property: variantProp)"
    }

    def "variant annotation on property with primitive type raises error"() {
        buildFile << """
        @Managed
        interface SampleBinary extends BinarySpec {
            @Variant
            int getVariantProp()
            void setVariantProp(int variant)
        }
        class Rules extends RuleSource {
            @ComponentType
            void register(TypeBuilder<SampleBinary> builder) {}
        }
        apply plugin: Rules
        """

        expect:
        fails "components"
        failure.assertHasCause "Invalid managed model type SampleBinary: @Variant annotation only allowed for properties of type String and org.gradle.api.Named, but property has type int (invalid property: variantProp)"
    }

    def "variant annotation on property with boolean type raises error"() {
        buildFile << """
        @Managed
        interface SampleBinary extends BinarySpec {
            @Variant boolean isVariantProp()
            void setVariantProp(boolean variant)
        }
        class Rules extends RuleSource {
            @ComponentType
            void register(TypeBuilder<SampleBinary> builder) {}
        }
        apply plugin: Rules
        """

        expect:
        fails "components"
        failure.assertHasCause "Invalid managed model type SampleBinary: @Variant annotation only allowed for properties of type String and org.gradle.api.Named, but property has type boolean (invalid property: variantProp)"
    }

    def "variant annotation on setter raises error"() {
        buildFile << """
        @Managed
        interface SampleBinary extends BinarySpec {
            String getVariantProp()
            @Variant
            void setVariantProp(String variant)
        }
        class Rules extends RuleSource {
            @ComponentType
            void register(TypeBuilder<SampleBinary> builder) {}
        }
        apply plugin: Rules
        """

        expect:
        fails "components"
        failure.assertHasCause "Invalid managed model type SampleBinary: @Variant annotation is only allowed on getter methods (invalid property: variantProp)"
    }
}
