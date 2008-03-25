/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies

import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.InvalidUserDataException

/**
 * @author Hans Dockter
 */
class ResolverFactoryTest extends GroovyTestCase {
    static final String RESOLVER_URL = 'http://a.b.c/'
    static final Map RESOLVER_MAP = [name: 'mapresolver', url: 'http://x.y.z/']
    static final IBiblioResolver TEST_RESOLVER = new IBiblioResolver()
    static {
        TEST_RESOLVER.name = 'ivyResolver'
    }

    ResolverFactory factory

    void setUp() {
        factory = new ResolverFactory()
    }
    void testCreateResolver() {
        checkIBibiblioResolver(factory.createResolver(RESOLVER_URL), RESOLVER_URL, RESOLVER_URL)
        checkIBibiblioResolver(factory.createResolver(RESOLVER_MAP), RESOLVER_MAP.name, RESOLVER_MAP.url)
        DependencyResolver resolver = factory.createResolver(TEST_RESOLVER)
        assert resolver.is(TEST_RESOLVER)
        def someIllegalDescription = new NullPointerException()
        shouldFail(InvalidUserDataException) {
            factory.createResolver(someIllegalDescription)
        }
    }
    private void checkIBibiblioResolver(IBiblioResolver iBiblioResolver, String name, String url) {
        assertEquals url, iBiblioResolver.root
        assertEquals name, iBiblioResolver.name
    }
}
