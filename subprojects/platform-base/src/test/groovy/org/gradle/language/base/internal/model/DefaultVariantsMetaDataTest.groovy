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
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.Variant
import spock.lang.Specification

class DefaultVariantsMetaDataTest extends Specification {

    def "should extract variants from a binary spec"() {
        given:
        MyBinarySpec spec = Mock()

        when:
        spec.platform >> platform
        spec.flavor >> flavor
        spec.buildType >> buildType
        spec.notVariantDimension >> notVariantDimension
        def variants = DefaultVariantsMetaData.extractFrom(spec)

        then:
        variants.nonNullDimensions == (nonNullDimensions as Set)
        variants.allDimensions == (allDimensions as Set)
        variants.getValueAsString('platform') == platform
        variants.getValueAsString('flavor') == flavor
        variants.getValueAsString('buildType') == buildType?.name
        variants.getValueAsType(BuildType, 'buildType') == buildType
        variants.getValueAsString('notVariantDimension') == null

        where:
        platform | flavor  | buildType       | notVariantDimension | nonNullDimensions                   | allDimensions
        'java6'  | null    | null            | null                | ['platform']                        | ['platform', 'flavor', 'buildType']
        'java6'  | null    | null            | 'foo'               | ['platform']                        | ['platform', 'flavor', 'buildType']
        'java6'  | 'debug' | null            | null                | ['platform', 'flavor']              | ['platform', 'flavor', 'buildType']
        'java6'  | 'debug' | Mock(BuildType) | null                | ['platform', 'flavor', 'buildType'] | ['platform', 'flavor', 'buildType']

    }

    private static interface MyBinarySpec extends BinarySpec {
        @Variant
        String getPlatform()

        @Variant
        String getFlavor()

        @Variant
        BuildType getBuildType()

        String getNotVariantDimension()
    }

    private static interface BuildType extends Named {}
}
