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

package org.gradle.language.base.internal.model
import org.gradle.api.Named
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractor
import org.gradle.model.internal.manage.schema.extract.ModelSchemaExtractor
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.Platform
import org.gradle.platform.base.Variant
import org.gradle.platform.base.internal.VariantAspectExtractionStrategy
import spock.lang.Specification
import spock.lang.Unroll

class VariantsMetaDataHelperTest extends Specification {

    def schemaStore = new DefaultModelSchemaStore(new ModelSchemaExtractor([], new ModelSchemaAspectExtractor([new VariantAspectExtractionStrategy()])))

    @Unroll("Incompatible variant dimensions for #referenceClass.simpleName(#dimensions) onto #candidateClass.simpleName are #expectedIncompatible")
    def "computes the set of incompatible variant dimensions"() {
        given:
        def reference = DefaultVariantsMetaData.extractFrom(Mock(referenceClass), schemaStore)
        def candidate = DefaultVariantsMetaData.extractFrom(Mock(candidateClass), schemaStore)

        when:
        def incompatibleDimensions = VariantsMetaDataHelper.incompatibleDimensionTypes(reference, candidate, dimensions as Set)

        then:
        incompatibleDimensions == (expectedIncompatible as Set)

        where:
        referenceClass           | candidateClass                      | dimensions                           | expectedIncompatible
        Binary1                  | Binary1                             | ['variant1', 'variant2', 'platform'] | []
        Binary1                  | Binary1                             | ['variant1']                         | []
        Binary1                  | Binary2                             | ['variant1', 'variant2', 'platform'] | ['variant2']
        Binary2                  | Binary3                             | ['variant1', 'variant2', 'platform'] | ['variant2']
        Binary2                  | Binary4                             | ['variant1', 'variant2', 'platform'] | []
        ParametrizedBinaryString | ParametrizedBinaryString            | ['variant']                          | []
        ParametrizedBinaryString | ParametrizedBinaryVariantDimension1 | ['variant']                          | []
    }

    public static interface Binary1 extends BinarySpec {
        @Variant
        String getVariant1()

        @Variant
        String getVariant2()

        @Variant
        Platform getPlaform()
    }

    public static interface Binary2 extends BinarySpec {
        @Variant
        String getVariant1()

        @Variant
        VariantDimension1 getVariant2()

        @Variant
        Platform getPlaform()
    }

    public static interface Binary3 extends BinarySpec {
        @Variant
        String getVariant1()

        @Variant
        VariantDimension2 getVariant2()

        @Variant
        Platform getPlaform()
    }

    public static interface Binary4 extends BinarySpec {
        @Variant
        String getVariant1()

        @Variant
        VariantDimension3 getVariant2()

        @Variant
        Platform getPlaform()
    }

    public static interface ParametrizedBinary<T> extends BinarySpec {
        @Variant
        ParametrizedVariant<T> getVariant()
    }

    public static interface ParametrizedBinaryString extends ParametrizedBinary<String> {}

    public static interface ParametrizedBinaryVariantDimension1 extends ParametrizedBinary<VariantDimension1> {}

    public static interface VariantDimension1 extends Named {}

    public static interface VariantDimension2 extends Named {}

    public static interface VariantDimension3 extends VariantDimension1 {}

    public static interface ParametrizedVariant<T> extends Named {
        T blah()
    }
}
