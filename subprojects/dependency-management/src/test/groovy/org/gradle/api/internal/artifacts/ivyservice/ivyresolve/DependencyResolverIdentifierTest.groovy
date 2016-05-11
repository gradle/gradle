/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import spock.lang.Specification

import java.lang.reflect.Field

public class DependencyResolverIdentifierTest extends Specification {
    private final static Field IVY = ExternalResourceResolver.getDeclaredField('ivyPatterns')
    private final static Field ARTIFACT = ExternalResourceResolver.getDeclaredField('artifactPatterns')

    def "dependency resolvers of type ExternalResourceResolver are differentiated by their patterns"() {
        given:
        ExternalResourceResolver resolver1 = Mock()
        ExternalResourceResolver resolver1a = Mock()
        ExternalResourceResolver resolver2 = Mock()
        ExternalResourceResolver resolver2a = Mock()

        patterns(resolver1, IVY, ['ivy1', 'ivy2'])
        patterns(resolver1, ARTIFACT, ['artifact1', 'artifact2'])
        patterns(resolver1a, IVY, ['ivy1', 'ivy2'])
        patterns(resolver1a, ARTIFACT, ['artifact1', 'artifact2'])
        patterns(resolver2, IVY, ['ivy1', 'different'])
        patterns(resolver2, ARTIFACT, ['artifact1', 'artifact2'])
        patterns(resolver2a, IVY, ['ivy1', 'ivy2'])
        patterns(resolver2a, ARTIFACT, ['artifact1', 'different'])

        expect:
        id(resolver1) == id(resolver1a)
        id(resolver1) != id(resolver2)
        id(resolver1) != id(resolver2a)
        id(resolver2) != id(resolver2a)
    }

    def "dependency resolvers of type ExternalResourceResolver are differentiated by m2compatible flag"() {
        given:
        ExternalResourceResolver resolver1 = Mock()
        ExternalResourceResolver resolver2 = Mock()

        patterns(resolver1, IVY, ['ivy1'])
        patterns(resolver1, ARTIFACT, ['artifact1'])
        patterns(resolver2, IVY, ['ivy1'])
        patterns(resolver2, ARTIFACT,['artifact1'])
        resolver2.m2compatible >> true

        expect:
        id(resolver1) != id(resolver2)
    }

    def patterns(ExternalResourceResolver resolver, Field field, List<String> patterns) {
        field.accessible = true
        field.set(resolver, patterns.collect { p -> Mock(ResourcePattern) {
            getPattern() >> p
        }})
    }

    def id(ExternalResourceResolver resolver) {
        ExternalResourceResolver.generateId(resolver)
    }
}
