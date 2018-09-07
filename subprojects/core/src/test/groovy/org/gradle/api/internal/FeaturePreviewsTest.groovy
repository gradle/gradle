/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal

import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.FeaturePreviews.Feature.GRADLE_METADATA
import static org.gradle.api.internal.FeaturePreviews.Feature.IMPROVED_POM_SUPPORT
import static org.gradle.api.internal.FeaturePreviews.Feature.STABLE_PUBLISHING

class FeaturePreviewsTest extends Specification {

    def 'has no features enabled by default'() {
        given:
        def previews = new FeaturePreviews()
        when:
        def result = previews.isFeatureEnabled(feature)
        then:
        !result
        where:
        feature << [IMPROVED_POM_SUPPORT, GRADLE_METADATA]
    }

    @Unroll
    def "can enable #feature"() {
        given:
        def previews = new FeaturePreviews()
        when:
        previews.enableFeature(feature)
        then:
        previews.isFeatureEnabled(feature)
        where:
        feature << [IMPROVED_POM_SUPPORT, GRADLE_METADATA]
    }

    @Unroll
    def "can enable #feature as String"() {
        given:
        def previews = new FeaturePreviews()
        when:
        previews.enableFeature(feature)
        then:
        previews.isFeatureEnabled(feature)
        where:
        feature << ['IMPROVED_POM_SUPPORT', 'GRADLE_METADATA']
    }

    def 'fails when enabling an unknown feature'() {
        given:
        def previews = new FeaturePreviews()
        when:
        previews.enableFeature('UNKNOWN_FEATURE')
        then:
        def exception = thrown IllegalArgumentException
        exception.getMessage() == 'There is no feature named UNKNOWN_FEATURE'
    }

    def 'fails when querying an unknown feature'() {
        given:
        def previews = new FeaturePreviews()
        when:
        previews.isFeatureEnabled('UNKNOWN_FEATURE')
        then:
        def exception = thrown IllegalArgumentException
        exception.getMessage() == 'There is no feature named UNKNOWN_FEATURE'
    }

    def 'lists active features'() {
        given:
        def previews = new FeaturePreviews()
        expect:
        previews.getActiveFeatures() == [IMPROVED_POM_SUPPORT, GRADLE_METADATA, STABLE_PUBLISHING] as Set
    }
}
