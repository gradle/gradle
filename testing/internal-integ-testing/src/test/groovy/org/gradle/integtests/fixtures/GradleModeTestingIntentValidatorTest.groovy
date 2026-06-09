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

import spock.lang.Specification

import java.lang.reflect.Method

@SuppressWarnings('unused')
class GradleModeTestingIntentValidatorTest extends Specification {

    // --- Fixture classes covering the truth table ---
    //
    // Layout: base classes carry class-level intents; child classes either inherit
    // cleanly or add their own class/method-level intents. The "feature methods"
    // are just regular methods named feat() / featAnnotated*().

    static class CleanBase {
        void feat() {}
    }

    @ToBeFixedForIsolatedProjects
    static class ToBeFixedIpBase {
        void feat() {}
    }

    @UnsupportedWithIsolatedProjects
    static class UnsupportedIpBase {
        void feat() {}
    }

    // OK cases

    static class CleanChild extends CleanBase {}

    static class ChildWithMethodToBeFixedIp extends CleanBase {
        @ToBeFixedForIsolatedProjects
        void featAnnotated() {}
    }

    static class ChildWithMethodUnsupportedIp extends CleanBase {
        @UnsupportedWithIsolatedProjects
        void featAnnotated() {}
    }

    static class ChildInheritingToBeFixedIp extends ToBeFixedIpBase {}

    static class ChildInheritingUnsupportedIp extends UnsupportedIpBase {}

    // Cross-mode (different modes do not interfere)

    @UnsupportedWithConfigurationCache
    static class ChildClassCcMethodIp extends CleanBase {
        @ToBeFixedForIsolatedProjects
        void featAnnotated() {}
    }

    // FAIL: class-vs-method conflict, same mode

    @UnsupportedWithIsolatedProjects
    static class ChildClassUnsupportedIpMethodToBeFixedIp extends CleanBase {
        @ToBeFixedForIsolatedProjects
        void featAnnotated() {}
    }

    @ToBeFixedForIsolatedProjects
    static class ChildClassToBeFixedIpMethodUnsupportedIp extends CleanBase {
        @UnsupportedWithIsolatedProjects
        void featAnnotated() {}
    }

    // FAIL: hierarchy has two class-level intents (same mode)

    @UnsupportedWithIsolatedProjects
    static class ChildClassUnsupportedIpOverToBeFixedIpBase extends ToBeFixedIpBase {}

    @ToBeFixedForIsolatedProjects
    static class ChildClassToBeFixedIpOverUnsupportedIpBase extends UnsupportedIpBase {}

    // FAIL: superclass class-level + child method-level (same mode)

    static class ChildInheritsUnsupportedIpMethodToBeFixedIp extends UnsupportedIpBase {
        @ToBeFixedForIsolatedProjects
        void featAnnotated() {}
    }

    static class ChildInheritsToBeFixedIpMethodUnsupportedIp extends ToBeFixedIpBase {
        @UnsupportedWithIsolatedProjects
        void featAnnotated() {}
    }

    // FAIL: two method-level intents of the same mode on one method

    static class TwoMethodIntentsSameMode extends CleanBase {
        @ToBeFixedForIsolatedProjects
        @UnsupportedWithIsolatedProjects
        void featAnnotated() {}
    }

    // FAIL: same annotation declared on base AND child (same type repeated through hierarchy)

    @ToBeFixedForIsolatedProjects
    static class ChildClassToBeFixedIpOverToBeFixedIpBase extends ToBeFixedIpBase {}

    // FAIL (CC variant): exercise mode-agnostic behavior — same rule must fire for Configuration Cache

    @UnsupportedWithConfigurationCache
    static class CcBase {
        void feat() {}
    }

    static class ChildInheritsCcUnsupportedMethodCcToBeFixed extends CcBase {
        @ToBeFixedForConfigurationCache
        void featAnnotated() {}
    }

    // --- The truth table ---

    def "validateFeature: #scenario"() {
        given:
        Method method = findFeatureMethod(specClass)

        when:
        GradleModeTestingIntentValidator.validateFeature(specClass, method)

        then:
        noExceptionThrown()

        where:
        scenario                                              | specClass
        "clean spec, no annotations"                          | CleanChild
        "method-only ToBeFixedIp"                             | ChildWithMethodToBeFixedIp
        "method-only UnsupportedIp"                           | ChildWithMethodUnsupportedIp
        "class-only ToBeFixedIp inherited"                    | ChildInheritingToBeFixedIp
        "class-only UnsupportedIp inherited"                  | ChildInheritingUnsupportedIp
        "class CC + method IP (different modes, no conflict)" | ChildClassCcMethodIp
    }

    def "validateFeature throws: #scenario"() {
        given:
        Method method = findFeatureMethod(specClass)

        when:
        GradleModeTestingIntentValidator.validateFeature(specClass, method)

        then:
        IllegalStateException ex = thrown()
        for (String expected : expectedMessageFragments) {
            assert ex.message.contains(expected): "expected fragment '${expected}' in: ${ex.message}"
        }

        where:
        scenario                                                      | specClass                                   | expectedMessageFragments
        "class UnsupportedIp + method ToBeFixedIp"                    | ChildClassUnsupportedIpMethodToBeFixedIp    | ["Isolated Projects", "UnsupportedWithIsolatedProjects", "ToBeFixedForIsolatedProjects"]
        "class ToBeFixedIp + method UnsupportedIp"                    | ChildClassToBeFixedIpMethodUnsupportedIp    | ["Isolated Projects", "ToBeFixedForIsolatedProjects", "UnsupportedWithIsolatedProjects"]
        "hierarchy: child UnsupportedIp over base ToBeFixedIp"        | ChildClassUnsupportedIpOverToBeFixedIpBase  | ["Isolated Projects", "UnsupportedWithIsolatedProjects", "ToBeFixedForIsolatedProjects"]
        "hierarchy: child ToBeFixedIp over base UnsupportedIp"        | ChildClassToBeFixedIpOverUnsupportedIpBase  | ["Isolated Projects", "ToBeFixedForIsolatedProjects", "UnsupportedWithIsolatedProjects"]
        "inherited class UnsupportedIp + method ToBeFixedIp on child" | ChildInheritsUnsupportedIpMethodToBeFixedIp | ["Isolated Projects", "UnsupportedWithIsolatedProjects", "ToBeFixedForIsolatedProjects"]
        "inherited class ToBeFixedIp + method UnsupportedIp on child" | ChildInheritsToBeFixedIpMethodUnsupportedIp | ["Isolated Projects", "ToBeFixedForIsolatedProjects", "UnsupportedWithIsolatedProjects"]
        "two method-level intents of the same mode on one method"     | TwoMethodIntentsSameMode                    | ["Isolated Projects", "ToBeFixedForIsolatedProjects", "UnsupportedWithIsolatedProjects"]
        "same annotation on base and child (hierarchy)"               | ChildClassToBeFixedIpOverToBeFixedIpBase    | ["Isolated Projects", "ToBeFixedForIsolatedProjects"]
        "CC variant: inherited UnsupportedCc + method ToBeFixedCc"    | ChildInheritsCcUnsupportedMethodCcToBeFixed | ["Configuration Cache", "UnsupportedWithConfigurationCache", "ToBeFixedForConfigurationCache"]
    }

    def "validateSpec: #scenario"() {
        when:
        GradleModeTestingIntentValidator.validateSpec(specClass)

        then:
        noExceptionThrown()

        where:
        scenario                                | specClass
        "clean spec, no class-level intents"    | CleanChild
        "single class-level ToBeFixedIp"        | ChildInheritingToBeFixedIp
        "single class-level UnsupportedIp"      | ChildInheritingUnsupportedIp
        "method-level intent does not concern"  | ChildWithMethodToBeFixedIp
        "two intents of different modes are ok" | ChildClassCcMethodIp
    }

    def "validateSpec throws: #scenario"() {
        when:
        GradleModeTestingIntentValidator.validateSpec(specClass)

        then:
        IllegalStateException ex = thrown()
        for (String expected : expectedMessageFragments) {
            assert ex.message.contains(expected): "expected fragment '${expected}' in: ${ex.message}"
        }

        where:
        scenario                                               | specClass                                  | expectedMessageFragments
        "hierarchy: child UnsupportedIp over base ToBeFixedIp" | ChildClassUnsupportedIpOverToBeFixedIpBase | ["Isolated Projects", "UnsupportedWithIsolatedProjects", "ToBeFixedForIsolatedProjects"]
        "hierarchy: child ToBeFixedIp over base UnsupportedIp" | ChildClassToBeFixedIpOverUnsupportedIpBase | ["Isolated Projects", "ToBeFixedForIsolatedProjects", "UnsupportedWithIsolatedProjects"]
        "same annotation on base and child (hierarchy)"        | ChildClassToBeFixedIpOverToBeFixedIpBase   | ["Isolated Projects", "ToBeFixedForIsolatedProjects"]
    }

    def "per-class verdict is cached and reused across feature lookups"() {
        given:
        Method m1 = findFeatureMethod(ChildInheritingToBeFixedIp)

        when:
        GradleModeTestingIntentValidator.validateFeature(ChildInheritingToBeFixedIp, m1)
        GradleModeTestingIntentValidator.validateFeature(ChildInheritingToBeFixedIp, m1)

        then:
        noExceptionThrown()
    }

    private static Method findFeatureMethod(Class<?> specClass) {
        for (Class<?> c = specClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.name == "featAnnotated" || m.name == "feat") {
                    return m
                }
            }
        }
        throw new IllegalStateException("No feature method on ${specClass}")
    }
}
