/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.jvm.internal.resolve

import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaExtractor
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractor
import org.gradle.platform.base.Platform
import org.gradle.platform.base.Variant
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.VariantAspectExtractionStrategy
import spock.lang.Specification
import spock.lang.Unroll

class VariantsMetaDataHelperTest extends Specification {
    def schemaStore = new DefaultModelSchemaStore(DefaultModelSchemaExtractor.withDefaultStrategies([], new ModelSchemaAspectExtractor([new VariantAspectExtractionStrategy()])))

    @Unroll("Incompatible variant dimensions for #referenceClass.simpleName(#dimensions) onto #candidateClass.simpleName are #expectedIncompatible")
    def "computes the set of incompatible variant dimensions"() {
        given:
        def referenceBinary = binary(referenceClass)
        def candidateBinary = binary(candidateClass)
        def reference = DefaultVariantsMetaData.extractFrom(referenceBinary, schemaStore.getSchema(referenceBinary.publicType))
        def candidate = DefaultVariantsMetaData.extractFrom(candidateBinary, schemaStore.getSchema(candidateBinary.publicType))

        when:
        def incompatibleDimensions = VariantsMetaDataHelper.determineAxesWithIncompatibleTypes(reference, candidate, dimensions as Set)

        then:
        incompatibleDimensions == (expectedIncompatible as Set)

        where:
        referenceClass                      | candidateClass                      | dimensions                           | expectedIncompatible
        Binary1                             | Binary1                             | ['variant1', 'variant2', 'platform'] | []
        Binary1                             | Binary1                             | ['variant1']                         | []
        Binary1                             | Binary2                             | ['variant1', 'variant2', 'platform'] | ['variant2']
        Binary2                             | Binary3                             | ['variant1', 'variant2', 'platform'] | ['variant2']
        Binary2                             | Binary4                             | ['variant1', 'variant2', 'platform'] | []
        ParameterizedBinaryString            | ParameterizedBinaryString            | ['variant']                          | []
        ParameterizedBinaryVariantDimension1 | ParameterizedBinaryVariantDimension1 | ['variant']                          | []
        ParameterizedBinaryString            | ParameterizedBinaryVariantDimension1 | ['variant']                          | ['variant']
    }

    private BinarySpecInternal binary(Class<? extends BinarySpecInternal> type) {
        def spec = Mock(type)
        spec.publicType >> type
        return spec
    }

    public static interface Binary1 extends BinarySpecInternal {
        @Variant
        String getVariant1()

        @Variant
        String getVariant2()

        @Variant
        Platform getPlatform()
    }

    public static interface Binary2 extends BinarySpecInternal {
        @Variant
        String getVariant1()

        @Variant
        VariantDimension1 getVariant2()

        @Variant
        Platform getPlatform()
    }

    public static interface Binary3 extends BinarySpecInternal {
        @Variant
        String getVariant1()

        @Variant
        VariantDimension2 getVariant2()

        @Variant
        Platform getPlatform()
    }

    public static interface Binary4 extends BinarySpecInternal {
        @Variant
        String getVariant1()

        @Variant
        VariantDimension3 getVariant2()

        @Variant
        Platform getPlatform()
    }
}
