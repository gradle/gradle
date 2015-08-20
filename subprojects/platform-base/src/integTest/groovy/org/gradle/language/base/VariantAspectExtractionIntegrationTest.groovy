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
import spock.lang.Unroll

class VariantAspectExtractionIntegrationTest extends AbstractIntegrationSpec {
    def "variant annotation on property with illegal type type raises error"() {
        buildFile << """
        interface SampleBinary extends BinarySpec {
            @Variant
            Integer getVariantProp()
            void setVariantProp(Integer variant)
        }
        class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
            Integer variantProp
        }
        class Rules extends RuleSource {
            @BinaryType
            void register(BinaryTypeBuilder<SampleBinary> builder) {
                builder.defaultImplementation(DefaultSampleBinary)
            }
        }
        apply plugin: Rules
        """

        expect:
        fails "components"
        failure.assertHasCause "Invalid managed model type SampleBinary: @Variant annotation only allowed for properties of type String and org.gradle.api.Named, but property has type java.lang.Integer (invalid property: variantProp)"
    }

    def "variant annotation on property with primitive type type raises error"() {
        buildFile << """
        interface SampleBinary extends BinarySpec {
            @Variant
            int getVariantProp()
            void setVariantProp(int variant)
        }
        class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
            int variantProp
        }
        class Rules extends RuleSource {
            @BinaryType
            void register(BinaryTypeBuilder<SampleBinary> builder) {
                builder.defaultImplementation(DefaultSampleBinary)
            }
        }
        apply plugin: Rules
        """

        expect:
        fails "components"
        failure.assertHasCause "Invalid managed model type SampleBinary: @Variant annotation only allowed for properties of type String and org.gradle.api.Named, but property has type int (invalid property: variantProp)"
    }

    @Unroll
    def "variant annotation on property with boolean type and #getterDesc getter raises error"() {
        buildFile << """
        interface SampleBinary extends BinarySpec {
            $getter
            void setVariantProp(boolean variant)
        }
        class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
            boolean variantProp
        }
        class Rules extends RuleSource {
            @BinaryType
            void register(BinaryTypeBuilder<SampleBinary> builder) {
                builder.defaultImplementation(DefaultSampleBinary)
            }
        }
        apply plugin: Rules
        """

        expect:
        fails "components"
        failure.assertHasCause "Invalid managed model type SampleBinary: @Variant annotation only allowed for properties of type String and org.gradle.api.Named, but property has type boolean (invalid property: variantProp)"

        where:
        getterDesc                  | getter
        'get'                       | '@Variant boolean getVariantProp()'
        'is'                        | '@Variant boolean isVariantProp()'
        'both (annotation on is)'   | '@Variant boolean isVariantProp(); boolean getVariantProp()'
        'both (annotation on get)'  | 'boolean isVariantProp(); @Variant boolean getVariantProp()'
        'both (annotation on both)' | '@Variant boolean isVariantProp(); @Variant boolean getVariantProp()'
    }

    def "variant annotation on setter raises error"() {
        buildFile << """
        interface SampleBinary extends BinarySpec {
            String getVariantProp()
            @Variant
            void setVariantProp(String variant)
        }
        class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
            String variantProp
        }
        class Rules extends RuleSource {
            @BinaryType
            void register(BinaryTypeBuilder<SampleBinary> builder) {
                builder.defaultImplementation(DefaultSampleBinary)
            }
        }
        apply plugin: Rules
        """

        expect:
        fails "components"
        failure.assertHasCause "Invalid managed model type SampleBinary: @Variant annotation is only allowed on getter methods (invalid property: variantProp)"
    }
}
