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

import org.apache.ivy.plugins.resolver.AbstractPatternsBasedResolver
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver
import spock.lang.Specification

public class DependencyResolverIdentifierTest extends Specification {
    def "uses dependency resolver name"() {
        given:
        DependencyResolver resolver = Mock()
        resolver.name >> "resolver-name"

        expect:
        new DependencyResolverIdentifier(resolver).name == "resolver-name"
    }

    def "dependency resolvers of unknown type are identified by their name"() {
        given:
        DependencyResolver resolver1 = Mock()
        DependencyResolver resolver1a = Mock()
        DependencyResolver resolver2 = Mock()

        resolver1.name >> 'name1'
        resolver1a.name >> 'name1'
        resolver2.name >> 'name2'

        expect:
        id(resolver1) == id(resolver1a)
        id(resolver1) != id(resolver2)
    }

    def "dependency resolvers of type AbstractPatternBasedResolver are differentiated by their patterns"() {
        given:
        AbstractPatternsBasedResolver resolver1 = Mock()
        AbstractPatternsBasedResolver resolver1a = Mock()
        AbstractPatternsBasedResolver resolver2 = Mock()
        AbstractPatternsBasedResolver resolver2a = Mock()

        resolver1.ivyPatterns >> ['ivy1', 'ivy2']
        resolver1.artifactPatterns >> ['artifact1', 'artifact2']
        resolver1a.ivyPatterns >> ['ivy1', 'ivy2']
        resolver1a.artifactPatterns >> ['artifact1', 'artifact2']
        resolver2.ivyPatterns >> ['ivy1', 'different']
        resolver2.artifactPatterns >> ['artifact1', 'artifact2']
        resolver2a.ivyPatterns >> ['ivy1', 'ivy2']
        resolver2a.artifactPatterns >> ['artifact1', 'different']

        expect:
        id(resolver1) == id(resolver1a)
        id(resolver1) != id(resolver2)
        id(resolver1) != id(resolver2a)
        id(resolver2) != id(resolver2a)
    }

    def "dependency resolvers of type ExternalResourceResolver are differentiated by their patterns"() {
        given:
        ExternalResourceResolver resolver1 = Mock()
        ExternalResourceResolver resolver1a = Mock()
        ExternalResourceResolver resolver2 = Mock()
        ExternalResourceResolver resolver2a = Mock()

        resolver1.ivyPatterns >> ['ivy1', 'ivy2']
        resolver1.artifactPatterns >> ['artifact1', 'artifact2']
        resolver1a.ivyPatterns >> ['ivy1', 'ivy2']
        resolver1a.artifactPatterns >> ['artifact1', 'artifact2']
        resolver2.ivyPatterns >> ['ivy1', 'different']
        resolver2.artifactPatterns >> ['artifact1', 'artifact2']
        resolver2a.ivyPatterns >> ['ivy1', 'ivy2']
        resolver2a.artifactPatterns >> ['artifact1', 'different']

        expect:
        id(resolver1) == id(resolver1a)
        id(resolver1) != id(resolver2)
        id(resolver1) != id(resolver2a)
        id(resolver2) != id(resolver2a)
    }

    def "dependency resolvers of type AbstractPatternBasedResolver are differentiated by m2compatible flag"() {
        given:
        AbstractPatternsBasedResolver resolver1 = Mock()
        AbstractPatternsBasedResolver resolver2 = Mock()

        resolver1.ivyPatterns >> ['ivy1']
        resolver1.artifactPatterns >> ['artifact1']
        resolver1.m2compatible >> false
        resolver2.ivyPatterns >> ['ivy1']
        resolver2.artifactPatterns >> ['artifact1']
        resolver2.m2compatible >> true

        expect:
        id(resolver1) != id(resolver2)
    }

    def "dependency resolvers of type ExternalResourceResolver are differentiated by m2compatible flag"() {
        given:
        ExternalResourceResolver resolver1 = Mock()
        ExternalResourceResolver resolver2 = Mock()

        resolver1.ivyPatterns >> ['ivy1']
        resolver1.artifactPatterns >> ['artifact1']
        resolver2.ivyPatterns >> ['ivy1']
        resolver2.artifactPatterns >> ['artifact1']
        resolver2.m2compatible >> true

        expect:
        id(resolver1) != id(resolver2)
    }

    def id(DependencyResolver resolver) {
        return new DependencyResolverIdentifier(resolver).uniqueId
    }
}
