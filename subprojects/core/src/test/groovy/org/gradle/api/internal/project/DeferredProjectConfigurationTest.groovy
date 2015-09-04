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

package org.gradle.api.internal.project

import org.gradle.util.SetSystemProperties
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DeferredProjectConfigurationTest extends Specification {

    def project = TestUtil.createRootProject()
    def config = new DeferredProjectConfiguration(project)

    @Rule
    SetSystemProperties setSystemProperties

    def "can add config and fire"() {
        given:
        def events = []

        when:
        config.add { events << "a" }
        config.add { events << "b" }

        and:
        config.fire()

        then:
        events == ["a", "b"]
    }

    def "fire is idempotent"() {
        given:
        def events = []

        when:
        config.add { events << "a" }
        config.add { events << "b" }

        and:
        3.times { config.fire() }

        then:
        events == ["a", "b"]
    }

    def "cannot add actions once fired"() {
        when:
        config.fire()

        and:
        config.add {}

        then:
        def e = thrown IllegalStateException
        e.cause == null
    }

    def "can get trace info"() {
        given:
        System.setProperty("org.gradle.trace.deferred.project.configuration", "true")

        when:
        config.fire()

        and:
        config.add {}

        then:
        def e = thrown IllegalStateException
        e.cause instanceof Exception
    }

}
