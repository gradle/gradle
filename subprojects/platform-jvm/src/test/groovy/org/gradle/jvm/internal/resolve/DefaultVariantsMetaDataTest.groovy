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

import org.gradle.api.Named
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaExtractor
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractor
import org.gradle.platform.base.Variant
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.VariantAspectExtractionStrategy
import spock.lang.Specification

class DefaultVariantsMetaDataTest extends Specification {
    def schemaStore = new DefaultModelSchemaStore(DefaultModelSchemaExtractor.withDefaultStrategies([], new ModelSchemaAspectExtractor([new VariantAspectExtractionStrategy()])))

    def "should extract variants from a binary spec"() {
        given:
        def spec = Mock(MyBinarySpec)

        when:
        spec.publicType >> MyBinarySpec
        spec.platform >> platform
        spec.flavor >> flavor
        spec.buildType >> buildType
        spec.notVariantAxis >> notVariantAxis
        def variants = DefaultVariantsMetaData.extractFrom(spec, schemaStore.getSchema(((BinarySpecInternal)spec).getPublicType()))

        then:
        variants.nonNullVariantAxes == (nonNullVariantAxes as Set)
        variants.declaredVariantAxes == (allVariantAxes as Set)
        variants.getValueAsString('platform') == platform
        variants.getValueAsString('flavor') == flavor
        variants.getValueAsString('buildType') == buildType?.name
        variants.getValueAsType(BuildType, 'buildType') == buildType
        variants.getValueAsString('notVariantAxis') == null

        where:
        platform | flavor  | buildType       | notVariantAxis | nonNullVariantAxes                  | allVariantAxes
        'java6'  | null    | null            | null                | ['platform']                        | ['platform', 'flavor', 'buildType']
        'java6'  | null    | null            | 'foo'               | ['platform']                        | ['platform', 'flavor', 'buildType']
        'java6'  | 'debug' | null            | null                | ['platform', 'flavor']              | ['platform', 'flavor', 'buildType']
        'java6'  | 'debug' | Mock(BuildType) | null                | ['platform', 'flavor', 'buildType'] | ['platform', 'flavor', 'buildType']

    }

    private static interface MyBinarySpec extends BinarySpecInternal {
        @Variant
        String getPlatform()

        @Variant
        String getFlavor()

        @Variant
        BuildType getBuildType()

        String getNotVariantAxis()
    }

    private static interface BuildType extends Named {}
}
