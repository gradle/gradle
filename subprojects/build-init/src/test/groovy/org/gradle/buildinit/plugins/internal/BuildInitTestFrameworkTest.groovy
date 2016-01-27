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

class BuildInitTestFrameworkTest extends Specification {

    def "should convert valid test framework from string"() {
        when:
        def result = BuildInitTestFramework.fromName("spock")

        then:
        result == BuildInitTestFramework.SPOCK
    }

    def "should convert null to none"() {
        when:
        def result = BuildInitTestFramework.fromName(null)

        then:
        result == BuildInitTestFramework.NONE
    }

    def "should throw exception for unknown test framework"() {
        when:
        BuildInitTestFramework.fromName("unknown")

        then:
        GradleException e = thrown()
        e.message == "The requested test framework 'unknown' is not supported."
    }

    def "should list all supported test frameworks"() {
        when:
        def result = BuildInitTestFramework.listSupported();

        then:
        result.size() == 2
        result[0] == "spock"
        result[1] == "testng"
    }
}
