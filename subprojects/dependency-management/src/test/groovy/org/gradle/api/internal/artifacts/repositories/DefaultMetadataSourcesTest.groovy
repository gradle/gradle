/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories

import org.gradle.caching.internal.DefaultBuildCacheHasher
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification
import spock.lang.Unroll

class DefaultMetadataSourcesTest extends Specification {

    final Instantiator instantiator = Mock() {
        newInstance(_, _) >> { args ->
            Mock(TestMetadata) {
                getName() >> args[0].simpleName
                appendId(_) >> { args2 ->
                    args2[0].putString(args[0].name)
                }
            }
        }
    }

    def "preserves order of declaration and distinguishes sources"() {
        given:
        def sources = new DefaultMetadataSources()
        def build = { build(sources) }
        def hash = {
            def hasher = new DefaultBuildCacheHasher()
            build().appendId(hasher)
            hasher.hash()
        }

        def h0 = hash()

        when:
        sources.gradleMetadata()
        def h1 = hash()

        then:
        build().sources().name == ['DefaultGradleModuleMetadataSource']

        when:
        sources.artifact()
        def h2 = hash()

        then:
        build().sources().name == ['DefaultGradleModuleMetadataSource', 'DefaultArtifactMetadataSource']

        when:
        sources.reset()
        def h3 = hash()

        then:
        build().sources().empty

        when:
        sources.artifact()
        sources.gradleMetadata()
        def h4 = hash()

        then:
        build().sources().name == ['DefaultArtifactMetadataSource', 'DefaultGradleModuleMetadataSource']

        and:
        h0 == h3
        h0 != h1
        h1 != h2
        h2 != h3
        h4 != h2
    }

    @Unroll
    def "shorthand notation method #method creates #type"() {
        def sources = new DefaultMetadataSources()

        when:
        sources."$method"()

        then:
        sources.asImmutable(instantiator).sources().name == [type]

        where:
        method           | type
        'gradleMetadata' | 'DefaultGradleModuleMetadataSource'
        'ivyDescriptor'  | 'DefaultIvyDescriptorMetadataSource'
        'mavenPom'       | 'DefaultMavenPomMetadataSource'
        'artifact'       | 'DefaultArtifactMetadataSource'
    }

    private ImmutableMetadataSources build(DefaultMetadataSources sources) {
        sources.asImmutable(instantiator)
    }


    interface TestMetadata extends MetadataSource<? extends MutableModuleComponentResolveMetadata> {
        String getName()
    }
}
