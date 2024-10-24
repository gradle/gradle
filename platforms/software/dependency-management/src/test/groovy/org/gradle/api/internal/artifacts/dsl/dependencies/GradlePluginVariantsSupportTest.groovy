/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class GradlePluginVariantsSupportTest extends Specification {

    def attributes = AttributeTestUtil.attributesFactory()
    def objects = TestUtil.objectFactory()
    def matcher = newMatcher()

    static AttributeMatcher newMatcher() {
        def schema = AttributeTestUtil.immutableSchema {
            GradlePluginVariantsSupport.configureSchema(it)
        }
        return AttributeTestUtil.services().getMatcher(schema, ImmutableAttributesSchema.EMPTY)
    }

    def "Gradle #currentGradleVersion #acceptsOrRejects 7.0 api"() {
        given:
        def accepts = acceptsOrRejects == 'accepts'

        when:
        def consumer = versionAttribute(currentGradleVersion)
        def producer = versionAttribute('7.0')

        then:
        accepts == (matcher.matchMultipleCandidates([producer], consumer) == [producer])
        accepts == matcher.isMatchingCandidate(producer, consumer)

        where:
        currentGradleVersion       | acceptsOrRejects
        '7.0'                      | 'accepts'
        '7.0-20210211230048+0000'  | 'accepts'
        '7.0-milestone-1'          | 'accepts'
        '7.0-rc-2'                 | 'accepts'
        '7.2'                      | 'accepts'
        '8.0'                      | 'accepts'

        '6.8.3'                    | 'rejects'
        '6.8-rc-2'                 | 'rejects'
        '5.0'                      | 'rejects'
    }

    def "chooses exact match API if available"() {
        when:
        def consumer = versionAttribute('7.0')
        def producer = [
            versionAttribute('6.0'),
            versionAttribute('7.0'),
            versionAttribute('7.0-rc-1'), // this is bad practice: targeting a not-GA version
            versionAttribute('7.1'),
            versionAttribute('8.0')
        ]

        then:
        matcher.matchMultipleCandidates(producer, consumer) == [versionAttribute('7.0')]

    }

    def "chooses closest API"() {
        when:
        def consumer = versionAttribute('7.2')
        def producer = [
            versionAttribute('6.0'),
            versionAttribute('7.0'),
            versionAttribute('7.1'), // 7.1 is the closest compatible (i.e. lower) version to 7.2 in this list
            versionAttribute('7.1-rc-2'), // this is bad practice: targeting a not-GA version
            versionAttribute('7.5'),
            versionAttribute('7.5'), // the duplicated '7.5' does not bother in this case
            versionAttribute('8.0')
        ]

        then:
        matcher.matchMultipleCandidates(producer, consumer) == [versionAttribute('7.1')]
    }

    def "fails to select one candidate if there is no clear preference"() {
        when:
        def consumer = versionAttribute('7.2')
        def producer = [
            versionAttribute('6.0'),
            versionAttribute('7.0'),
            versionAttribute('7.1'), // 7.1 is the closest to 7.2 in this list
            versionAttribute('7.1'),
            versionAttribute('8.0')
        ]

        then:
        matcher.matchMultipleCandidates(producer, consumer) == [versionAttribute('7.1'), versionAttribute('7.1')]
    }

    private ImmutableAttributes versionAttribute(String version) {
        def attributes = attributes.mutable()
        attributes.attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, objects.named(GradlePluginApiVersion, version))
        attributes.asImmutable()
    }
}
