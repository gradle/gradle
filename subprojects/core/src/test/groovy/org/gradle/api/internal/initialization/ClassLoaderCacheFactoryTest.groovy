/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization

import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class ClassLoaderCacheFactoryTest extends Specification {

    @Subject factory = new ClassLoaderCacheFactory()
    @Rule SetSystemProperties s = new SetSystemProperties()

    def "creates new instance if property is off"() {
        System.setProperty("org.gradle.caching.classloaders", "not true")
        expect:
        factory.create() != factory.create()
    }

    def "creates new instance if property is not configured"() {
        expect:
        factory.create() != factory.create()
    }

    def "reuses instance if property is on"() {
        System.setProperty("org.gradle.caching.classloaders", "True")
        expect:
        factory.create() == factory.create()
    }
}
