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
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class BuildInitBuildScriptDslTest extends Specification {

    def "should convert valid build script DSL from string"() {
        expect:
        BuildInitDsl.fromName("groovy") == BuildInitDsl.GROOVY

        and:
        BuildInitDsl.fromName("kotlin") == BuildInitDsl.KOTLIN
    }

    def "should convert null build script DSL string to the default kotlin"() {
        when:
        def result = BuildInitDsl.fromName(null)

        then:
        result == BuildInitDsl.KOTLIN
    }

    def "should throw exception for unknown build script DSL"() {
        when:
        BuildInitDsl.fromName("unknown")

        then:
        GradleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""The requested build script DSL 'unknown' is not supported. Supported DSLs:
  - 'kotlin'
  - 'groovy'""")
    }

    def "should list all supported build script DSLs"() {
        when:
        def result = BuildInitDsl.listSupported()

        then:
        result.size() == 2
        result[0] == "kotlin"
        result[1] == "groovy"
    }
}
