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

package org.gradle.nativeplatform.internal.configure

import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.plugins.NativeComponentModelPlugin
import spock.lang.Specification

class CreateDefaultBuildTypesTest extends Specification {
    def buildTypes = Mock(BuildTypeContainer)
    def rule = new NativeComponentModelPlugin.Rules()

    def "adds a default build type when none configured"() {
        when:
        rule.createDefaultBuildTypes(buildTypes)

        then:
        1 * buildTypes.empty >> true
        1 * buildTypes.create("debug")
        0 * buildTypes._
    }

    def "does not add default build type when some configured"() {
        when:
        rule.createDefaultBuildTypes(buildTypes)

        then:
        1 * buildTypes.empty >> false
        0 * buildTypes._
    }
}
