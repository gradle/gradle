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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import groovy.transform.TupleConstructor
import org.gradle.api.internal.artifacts.ResolveContext
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import spock.lang.Specification
import spock.lang.Unroll

class RequestScopeResolverProviderFactoryTest extends Specification {
    def "can create a ResolverProvider using the factory"() {
        given: "a factory of resolver providers"
        def instantiator = Mock(Instantiator)
        instantiator.newInstance(_, _) >> Mock(ResolverProvider)
        def factory = new RequestScopeResolverProviderFactory(instantiator)
        def mock = Mock(RequestScopeResolverProviderFactory.Query)
        mock.canCreateFrom(_) >> true

        when: "we call the create method using a resolve context"
        def resolver = factory.tryCreate(Mock(ResolveContext), mock)

        then: "a resolver provider is returned"
        resolver != null
    }

    def "cannot create a ResolverProvider using the factory if resolve context is not supported"() {
        given: "a factory of resolver providers"
        def instantiator = Mock(Instantiator)
        instantiator.newInstance(_, _) >> Mock(ResolverProvider)
        def factory = new RequestScopeResolverProviderFactory(instantiator)
        def mock = Mock(RequestScopeResolverProviderFactory.Query)
        mock.canCreateFrom(_) >> false

        when: "we call the create method using a resolve context"
        def resolver = factory.tryCreate(Mock(ResolveContext), mock)

        then: "a resolver provider is not returned"
        resolver == null
    }

    @Unroll
    def "can instantiate a ResolverProvider using factory and arguments (#a, #b, #c)"() {
        given: "a factory of resolver providers and a query"
        def instantiator = DirectInstantiator.INSTANCE
        def factory = new RequestScopeResolverProviderFactory(instantiator)

        when: "we call the create method using a resolve context"
        def query = new RequestScopeResolverProviderFactory.Query(
            DummyResolver,
            [a, b, c].findAll().toArray()
        )

        def context = Mock(ResolveContext)
        def resolver = factory.tryCreate(context, query)

        then: "a resolver provider is returned"
        resolver != null
        resolver instanceof DummyResolver
        resolver.ctx == context
        resolver.a == a
        resolver.b == b
        resolver.c == c

        where:
        a    | b    | c
        null | null | null
        '1'  | null | null
        '1'  | 2    | null
        '1'  | 2    | true

    }

    @TupleConstructor
    static class DummyResolver implements ResolverProvider {
        ResolveContext ctx
        Object a
        Object b
        Object c

        @Override
        DependencyToComponentIdResolver getComponentIdResolver() {
            return null
        }

        @Override
        ComponentMetaDataResolver getComponentResolver() {
            return null
        }

        @Override
        ArtifactResolver getArtifactResolver() {
            return null
        }
    }
}
