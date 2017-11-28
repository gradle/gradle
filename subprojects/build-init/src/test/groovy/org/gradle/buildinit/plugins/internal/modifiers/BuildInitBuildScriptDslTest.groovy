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
package org.gradle.buildinit.plugins.internal.modifiers

import org.gradle.api.GradleException
import spock.lang.Specification

class BuildInitBuildScriptDslTest extends Specification {

    def "should convert valid build script DSL from string"() {
        expect:
        BuildInitBuildScriptDsl.fromName("groovy") == BuildInitBuildScriptDsl.GROOVY

        and:
        BuildInitBuildScriptDsl.fromName("kotlin") == BuildInitBuildScriptDsl.KOTLIN
    }

    def "should convert null build script DSL string to the default groovy"() {
        when:
        def result = BuildInitBuildScriptDsl.fromName(null)

        then:
        result == BuildInitBuildScriptDsl.GROOVY
    }

    def "should throw exception for unknown build script DSL"() {
        when:
        BuildInitBuildScriptDsl.fromName("unknown")

        then:
        GradleException e = thrown()
        e.message == "The requested build script DSL 'unknown' is not supported."
    }

    def "should list all supported build script DSLs"() {
        when:
        def result = BuildInitBuildScriptDsl.listSupported()

        then:
        result.size() == 2
        result[0] == "groovy"
        result[1] == "kotlin"
    }
}
