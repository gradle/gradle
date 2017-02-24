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

package org.gradle.caching.configuration.internal

import org.gradle.StartParameter
import org.gradle.caching.configuration.AbstractBuildCache
import org.gradle.caching.local.LocalBuildCache
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultBuildCacheConfigurationTest extends Specification {
    private LocalBuildCache localBuildCache = new LocalBuildCache()
    private RemoteBuildCache remoteBuildCache = new RemoteBuildCache()
    private Instantiator instantiator = Stub(Instantiator) {
        newInstance(LocalBuildCache) >> { localBuildCache }
        newInstance(RemoteBuildCache) >> { remoteBuildCache }
    }
    private StartParameter startParameter = Stub(StartParameter) {
        getSystemPropertiesArgs() >> [:]
    }
    private DefaultBuildCacheConfiguration buildCacheConfiguration= new DefaultBuildCacheConfiguration(instantiator, [], startParameter)

    def 'push can be disabled via system property'() {
        def startParameter = Stub(StartParameter) {
            getSystemPropertiesArgs() >> ["org.gradle.cache.tasks.push": "false"]
        }
        buildCacheConfiguration = new DefaultBuildCacheConfiguration(instantiator, [], startParameter)
        buildCacheConfiguration.remote(RemoteBuildCache) {
            it.push = true
        }
        when:
        def localBuildCache = buildCacheConfiguration.getLocal()
        def remoteBuildCache = buildCacheConfiguration.getRemote()

        then:
        !localBuildCache.push
        !remoteBuildCache.push
    }

    def 'pull disabled is read from start parameter'() {
        def startParameter = Stub(StartParameter) {
            getSystemPropertiesArgs() >> ["org.gradle.cache.tasks.pull": "false"]
        }

        when:
        buildCacheConfiguration = new DefaultBuildCacheConfiguration(instantiator, [], startParameter)

        then:
        buildCacheConfiguration.isPullDisabled()
    }

    private static class RemoteBuildCache extends AbstractBuildCache {}
}
