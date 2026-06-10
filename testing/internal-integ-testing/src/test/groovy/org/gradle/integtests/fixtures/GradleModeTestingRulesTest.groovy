/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.junit.runner.Description
import org.junit.runners.model.Statement
import spock.lang.Specification

class GradleModeTestingRulesTest extends Specification {

    @ToBeFixedForIsolatedProjects
    static class ClassLevelToBeFixedIp {
        @SuppressWarnings('unused')
        void someTest() {}
    }

    def "rule throws when its annotation is declared on the JUnit class"() {
        given:
        def rule = GradleModeTestingRules.toBeFixedForIP()
        def description = Description.createTestDescription(ClassLevelToBeFixedIp, "someTest")
        Statement base = new Statement() {
            @Override
            void evaluate() {}
        }

        when:
        rule.apply(base, description)

        then:
        IllegalStateException ex = thrown()
        ex.message == "@ToBeFixedForIsolatedProjects on JUnit class ${ClassLevelToBeFixedIp.name} has no effect: " +
            "class-level Gradle-mode gating is only supported for Spock specs. " +
            "Apply the annotation to each affected test method instead."
    }
}
