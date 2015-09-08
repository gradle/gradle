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

package org.gradle.api.internal.project.antbuilder

import org.gradle.api.internal.classloading.GroovyJava7RuntimeMemoryLeakStrategy
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification

class GroovyJava7RuntimeMemoryLeakStrategyTest extends Specification {
    def "should not apply if Groovy is not found on classpath"() {
        given:
        def strategy = new GroovyJava7RuntimeMemoryLeakStrategy()

        when:
        def applies = strategy.appliesTo(new DefaultClassPath())

        then:
        applies == false
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should apply if Groovy is found on classpath"() {
        given:
        def strategy = new GroovyJava7RuntimeMemoryLeakStrategy()

        when:
        def applies = strategy.appliesTo(new DefaultClassPath(new File('groovy.jar')))

        then:
        applies == true
    }

}
