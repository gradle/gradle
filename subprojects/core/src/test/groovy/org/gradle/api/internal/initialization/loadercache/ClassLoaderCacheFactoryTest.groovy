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

import org.gradle.internal.environment.GradleBuildEnvironment
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class ClassLoaderCacheFactoryTest extends Specification {

    def environment = Stub(GradleBuildEnvironment)
    @Subject factory = new ClassLoaderCacheFactory(environment)
    @Rule SetSystemProperties s = new SetSystemProperties()

    def "creates new instance if property is off"() {
        when:
        environment.longLivingProcess >> false

        then:
        !factory.create().is(factory.create())
        factory.create().snapshotter instanceof FileClassPathSnapshotter
    }

    def "reuses class loader cache"() {
        when:
        environment.longLivingProcess >> true

        then:
        factory.create().is(factory.create())
        factory.create().snapshotter instanceof HashClassPathSnapshotter
    }
}
