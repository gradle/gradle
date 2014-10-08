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

package org.gradle.api.internal.initialization.loadercache

import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.initialization.loadercache.ClassLoaderCacheFactory.TOGGLE_CACHING_PROPERTY

class ClassLoaderCacheFactoryTest extends Specification {

    @Subject factory = new ClassLoaderCacheFactory()
    @Rule SetSystemProperties s = new SetSystemProperties()

    def "creates new instance if property is off"() {
        System.setProperty(TOGGLE_CACHING_PROPERTY, "not true")
        expect:
        factory.create() != factory.create()
        factory.create().snapshotter instanceof FileClassPathSnapshotter
    }

    def "creates new instance if property is not configured"() {
        expect:
        factory.create() != factory.create()
        factory.create().snapshotter instanceof FileClassPathSnapshotter
    }

    def "reuses instance if property is on"() {
        System.setProperty(TOGGLE_CACHING_PROPERTY, "True")
        expect:
        factory.create() == factory.create()
        factory.create().snapshotter instanceof HashClassPathSnapshotter
    }
}
