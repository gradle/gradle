/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;


import org.gradle.api.internal.artifacts.configurations.ForcedModule.InvalidDependencyFormat
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 10/14/11
 */
public class ForcedModuleTest extends Specification {

    def "understands gav notation"() {
        when:
        def v = new ForcedModule("org.foo:bar:1.0")

        then:
        v.group == 'org.foo'
        v.name  == 'bar'
        v.version  == '1.0'
    }

    def "reports invalid format"() {
        when:
        new ForcedModule("org.foo:bar1.0")

        then:
        thrown(InvalidDependencyFormat)
    }
}
