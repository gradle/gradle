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

package org.gradle.caching.internal

import org.gradle.api.GradleException
import org.gradle.caching.configuration.AbstractBuildCache
import spock.lang.Specification

class CompositeBuildCacheTest extends Specification {

    private CompositeBuildCache buildCache = new CompositeBuildCache()

    def 'cache to push to is selected'() {
        def pushToBuildCache = new TestBuildCache(push: true)

        when:
        buildCache.addDelegate(pushToBuildCache)
        buildCache.addDelegate(new TestBuildCache(push: false))

        then:
        buildCache.pushToCache.is(pushToBuildCache)
        buildCache.isPush()
    }

    def 'push is disabled if there is no cache to push to'() {
        when:
        buildCache.addDelegate(new TestBuildCache(push: false))
        buildCache.addDelegate(new TestBuildCache(push: false))

        then:
        !buildCache.isPush()
    }

    def 'push can be explicitly disabled'() {
        when:
        buildCache.addDelegate(new TestBuildCache(push: true))
        buildCache.addDelegate(new TestBuildCache(push: false))
        buildCache.push = false

        then:
        !buildCache.isPush()
    }

    def 'cannot add to caches with push enabled'() {
        when:
        buildCache.addDelegate(new TestBuildCache(push: true))
        buildCache.addDelegate(new TestBuildCache(push: true))

        then:
        def e = thrown(GradleException)
        e.message == 'Gradle only allows one build cache to be configured to push at a time. Disable push for one of the build caches.'
    }

    def 'disabled build caches are ignored'() {
        when:
        buildCache.addDelegate(null)
        buildCache.addDelegate(new TestBuildCache(enabled: false))

        then:
        buildCache.delegates.empty
    }

    private static class TestBuildCache extends AbstractBuildCache {}
}
