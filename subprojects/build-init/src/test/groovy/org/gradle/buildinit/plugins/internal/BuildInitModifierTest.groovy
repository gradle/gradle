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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.GradleException
import spock.lang.Specification

class BuildInitModifierTest extends Specification {

    def "should convert valid modifier from string"() {
        when:
        def result = BuildInitModifier.fromName("spock")

        then:
        result == BuildInitModifier.SPOCK
    }

    def "should convert null to none modifier"() {
        when:
        def result = BuildInitModifier.fromName(null)

        then:
        result == BuildInitModifier.NONE
    }

    def "should throw exception for unknown modifier"() {
        when:
        BuildInitModifier.fromName("unknown")

        then:
        GradleException e = thrown()
        e.message == "The requested init modifier 'unknown' is not supported."
    }
}
