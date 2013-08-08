/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult

import org.gradle.api.artifacts.Configuration
import spock.lang.Specification

class CachedStoreFactoryTest extends Specification {

    def "stores results"() {
        def factory = new CachedStoreFactory(20)

        def conf1 = Mock(Configuration)
        def conf2 = Mock(Configuration)

        def results1 = Mock(TransientConfigurationResults)
        def results2 = Mock(TransientConfigurationResults)

        def store1 = factory.createCachedStore(conf1)
        def store1b = factory.createCachedStore(conf1)
        def store2 = factory.createCachedStore(conf2)

        when:
        store1.store(results1)
        store2.store(results2)

        then:
        store1.load() == results1
        store1b.load() == results1
        store2.load() == results2
    }
}
