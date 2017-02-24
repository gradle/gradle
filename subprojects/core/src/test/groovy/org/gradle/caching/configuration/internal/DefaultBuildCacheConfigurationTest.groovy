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
import org.gradle.caching.local.LocalBuildCache
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultBuildCacheConfigurationTest extends Specification {
    def instantiator = Stub(Instantiator) {
        newInstance(_) >> Mock(LocalBuildCache)
    }

    def 'push disabled is read from start parameter'() {
        def startParameter = Stub(StartParameter) {
            getSystemPropertiesArgs() >> [(DefaultBuildCacheConfiguration.BUILD_CACHE_CAN_PUSH): "false"]
        }
        def buildCacheConfiguration = new DefaultBuildCacheConfiguration(instantiator, [], startParameter)
        expect:
        buildCacheConfiguration.isPushDisabled()
    }

    def 'pull disabled is read from start parameter'() {
        def startParameter = Stub(StartParameter) {
            getSystemPropertiesArgs() >> [(DefaultBuildCacheConfiguration.BUILD_CACHE_CAN_PULL): "false"]
        }
        def buildCacheConfiguration = new DefaultBuildCacheConfiguration(instantiator, [], startParameter)
        expect:
        buildCacheConfiguration.isPullDisabled()
    }
}
