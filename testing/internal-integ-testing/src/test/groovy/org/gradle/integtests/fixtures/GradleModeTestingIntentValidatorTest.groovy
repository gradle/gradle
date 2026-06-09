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

// classes and methods validated via reflection
@SuppressWarnings('unused')
class GradleModeTestingIntentValidatorTest extends Specification {

    // --- Accept cases ---

    def "validateFeature accepts non-conflicting spec: #scenario"() {
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

    def "validateSpec accepts non-conflicting hierarchy: #scenario"() {
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

    // --- IP: class-vs-method conflicts ---

    def "validateFeature rejects IP class-vs-method conflict: #scenario"() {
        given:
        Method method = findFeatureMethod(specClass)

        when:
        GradleModeTestingIntentValidator.validateFeature(specClass, method)

        then:
        IllegalStateException ex = thrown()
        assert normalize(ex.message, specClass) == "Conflicting Isolated Projects gating on <SPEC>: ${messagePart}." +
            " At most one intent annotation of a given mode may be reachable for a feature."

        where:
        scenario                                         | specClass
        "class Unsupported + method ToBeFixed"           | ChildClassUnsupportedIpMethodToBeFixedIp
        "class ToBeFixed + method Unsupported"           | ChildClassToBeFixedIpMethodUnsupportedIp
        "inherited class Unsupported + method ToBeFixed" | ChildInheritsUnsupportedIpMethodToBeFixedIp
        "inherited class ToBeFixed + method Unsupported" | ChildInheritsToBeFixedIpMethodUnsupportedIp
        __
        _ | messagePart
        _ | "@UnsupportedWithIsolatedProjects on class <SPEC> conflicts with @ToBeFixedForIsolatedProjects on method <SPEC>.featAnnotated()"
        _ | "@ToBeFixedForIsolatedProjects on class <SPEC> conflicts with @UnsupportedWithIsolatedProjects on method <SPEC>.featAnnotated()"
        _ | "@UnsupportedWithIsolatedProjects on class ${UnsupportedIpBase.name} conflicts with @ToBeFixedForIsolatedProjects on method <SPEC>.featAnnotated()"
        _ | "@ToBeFixedForIsolatedProjects on class ${ToBeFixedIpBase.name} conflicts with @UnsupportedWithIsolatedProjects on method <SPEC>.featAnnotated()"
    }

    // --- CC: class-vs-method conflicts ---

    def "validateFeature rejects CC class-vs-method conflict: #scenario"() {
        given:
        Method method = findFeatureMethod(specClass)

        when:
        GradleModeTestingIntentValidator.validateFeature(specClass, method)

        then:
        IllegalStateException ex = thrown()
        assert normalize(ex.message, specClass) == "Conflicting Configuration Cache gating on <SPEC>: ${messagePart}." +
            " At most one intent annotation of a given mode may be reachable for a feature."

        where:
        scenario                                         | specClass
        "class Unsupported + method ToBeFixed"           | ChildClassUnsupportedCcMethodToBeFixedCc
        "class ToBeFixed + method Unsupported"           | ChildClassToBeFixedCcMethodUnsupportedCc
        "inherited class Unsupported + method ToBeFixed" | ChildInheritsUnsupportedCcMethodToBeFixedCc
        "inherited class ToBeFixed + method Unsupported" | ChildInheritsToBeFixedCcMethodUnsupportedCc
        __
        _ | messagePart
        _ | "@UnsupportedWithConfigurationCache on class <SPEC> conflicts with @ToBeFixedForConfigurationCache on method <SPEC>.featAnnotated()"
        _ | "@ToBeFixedForConfigurationCache on class <SPEC> conflicts with @UnsupportedWithConfigurationCache on method <SPEC>.featAnnotated()"
        _ | "@UnsupportedWithConfigurationCache on class ${UnsupportedCcBase.name} conflicts with @ToBeFixedForConfigurationCache on method <SPEC>.featAnnotated()"
        _ | "@ToBeFixedForConfigurationCache on class ${ToBeFixedCcBase.name} conflicts with @UnsupportedWithConfigurationCache on method <SPEC>.featAnnotated()"
    }

    // --- IP: hierarchy conflicts ---

    def "validateSpec rejects IP hierarchy conflict: #scenario"() {
        when:
        GradleModeTestingIntentValidator.validateSpec(specClass)

        then:
        IllegalStateException ex = thrown()
        assert normalize(ex.message, specClass) == "Conflicting Isolated Projects gating on <SPEC>:" +
            " multiple class-level intent annotations are reachable for this mode, but at most one is allowed.\n" +
            messagePart

        where:
        scenario                                | specClass
        "child Unsupported over base ToBeFixed" | ChildClassUnsupportedIpOverToBeFixedIpBase
        "child ToBeFixed over base Unsupported" | ChildClassToBeFixedIpOverUnsupportedIpBase
        "same annotation on base and child"     | ChildClassToBeFixedIpOverToBeFixedIpBase
        __
        _ | messagePart
        _ | "  - @UnsupportedWithIsolatedProjects on class <SPEC>\n  - @ToBeFixedForIsolatedProjects on class ${ToBeFixedIpBase.name}\n"
        _ | "  - @ToBeFixedForIsolatedProjects on class <SPEC>\n  - @UnsupportedWithIsolatedProjects on class ${UnsupportedIpBase.name}\n"
        _ | "  - @ToBeFixedForIsolatedProjects on class <SPEC>\n  - @ToBeFixedForIsolatedProjects on class ${ToBeFixedIpBase.name}\n"
    }

    // --- CC: hierarchy conflicts ---

    def "validateSpec rejects CC hierarchy conflict: #scenario"() {
        when:
        GradleModeTestingIntentValidator.validateSpec(specClass)

        then:
        IllegalStateException ex = thrown()
        assert normalize(ex.message, specClass) == "Conflicting Configuration Cache gating on <SPEC>:" +
            " multiple class-level intent annotations are reachable for this mode, but at most one is allowed.\n" +
            messagePart

        where:
        scenario                                | specClass
        "child Unsupported over base ToBeFixed" | ChildClassUnsupportedCcOverToBeFixedCcBase
        "child ToBeFixed over base Unsupported" | ChildClassToBeFixedCcOverUnsupportedCcBase
        "same annotation on base and child"     | ChildClassToBeFixedCcOverToBeFixedCcBase
        __
        _ | messagePart
        _ | "  - @UnsupportedWithConfigurationCache on class <SPEC>\n  - @ToBeFixedForConfigurationCache on class ${ToBeFixedCcBase.name}\n"
        _ | "  - @ToBeFixedForConfigurationCache on class <SPEC>\n  - @UnsupportedWithConfigurationCache on class ${UnsupportedCcBase.name}\n"
        _ | "  - @ToBeFixedForConfigurationCache on class <SPEC>\n  - @ToBeFixedForConfigurationCache on class ${ToBeFixedCcBase.name}\n"
    }

    // --- Two method-level intents of the same mode ---

    def "validateFeature rejects two IP method-level intents on one method"() {
        Class<?> specClass = TwoMethodIntentsSameModeIp
        Method method = findFeatureMethod(specClass)

        when:
        GradleModeTestingIntentValidator.validateFeature(specClass, method)

        then:
        IllegalStateException ex = thrown()
        assert normalize(ex.message, specClass) == "Conflicting Isolated Projects gating on <SPEC>: " +
            "method <SPEC>.featAnnotated() carries multiple intent annotations for this mode: " +
            "@ToBeFixedForIsolatedProjects and @UnsupportedWithIsolatedProjects. At most one is allowed."
    }

    def "validateFeature rejects two CC method-level intents on one method"() {
        Class<?> specClass = TwoMethodIntentsSameModeCc
        Method method = findFeatureMethod(specClass)

        when:
        GradleModeTestingIntentValidator.validateFeature(specClass, method)

        then:
        IllegalStateException ex = thrown()
        assert normalize(ex.message, specClass) == "Conflicting Configuration Cache gating on <SPEC>: " +
            "method <SPEC>.featAnnotated() carries multiple intent annotations for this mode: " +
            "@ToBeFixedForConfigurationCache and @UnsupportedWithConfigurationCache. At most one is allowed."
    }

    // --- Expected-message builders ---


    private static String normalize(String message, Class<?> specClass) {
        return message.replace(specClass.name, "<SPEC>")
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

    // --- Fixture classes ---
    //
    // Base classes carry class-level intents; child classes either inherit cleanly
    // or add their own class/method-level intents. The "feature methods" are just
    // regular methods named feat() / featAnnotated().

    static class CleanBase {
        void feat() {}
    }

    // -- IP base classes --
    @ToBeFixedForIsolatedProjects
    static class ToBeFixedIpBase {
        void feat() {}
    }

    @UnsupportedWithIsolatedProjects
    static class UnsupportedIpBase {
        void feat() {}
    }

    // -- CC base classes --
    @ToBeFixedForConfigurationCache
    static class ToBeFixedCcBase {
        void feat() {}
    }

    @UnsupportedWithConfigurationCache
    static class UnsupportedCcBase {
        void feat() {}
    }

    // -- OK cases --
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

    // -- Cross-mode (no conflict; different modes do not interfere) --
    @UnsupportedWithConfigurationCache
    static class ChildClassCcMethodIp extends CleanBase {
        @ToBeFixedForIsolatedProjects
        void featAnnotated() {}
    }

    // -- IP: class A + method B (same mode) --
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

    // -- CC: class A + method B (same mode) --
    @UnsupportedWithConfigurationCache
    static class ChildClassUnsupportedCcMethodToBeFixedCc extends CleanBase {
        @ToBeFixedForConfigurationCache
        void featAnnotated() {}
    }

    @ToBeFixedForConfigurationCache
    static class ChildClassToBeFixedCcMethodUnsupportedCc extends CleanBase {
        @UnsupportedWithConfigurationCache
        void featAnnotated() {}
    }

    // -- IP: inherited class + method on child (same mode) --
    static class ChildInheritsUnsupportedIpMethodToBeFixedIp extends UnsupportedIpBase {
        @ToBeFixedForIsolatedProjects
        void featAnnotated() {}
    }

    static class ChildInheritsToBeFixedIpMethodUnsupportedIp extends ToBeFixedIpBase {
        @UnsupportedWithIsolatedProjects
        void featAnnotated() {}
    }

    // -- CC: inherited class + method on child (same mode) --
    static class ChildInheritsUnsupportedCcMethodToBeFixedCc extends UnsupportedCcBase {
        @ToBeFixedForConfigurationCache
        void featAnnotated() {}
    }

    static class ChildInheritsToBeFixedCcMethodUnsupportedCc extends ToBeFixedCcBase {
        @UnsupportedWithConfigurationCache
        void featAnnotated() {}
    }

    // -- IP: hierarchy has two class-level intents (same mode) --
    @UnsupportedWithIsolatedProjects
    static class ChildClassUnsupportedIpOverToBeFixedIpBase extends ToBeFixedIpBase {}

    @ToBeFixedForIsolatedProjects
    static class ChildClassToBeFixedIpOverUnsupportedIpBase extends UnsupportedIpBase {}

    @ToBeFixedForIsolatedProjects
    static class ChildClassToBeFixedIpOverToBeFixedIpBase extends ToBeFixedIpBase {}

    // -- CC: hierarchy has two class-level intents (same mode) --
    @UnsupportedWithConfigurationCache
    static class ChildClassUnsupportedCcOverToBeFixedCcBase extends ToBeFixedCcBase {}

    @ToBeFixedForConfigurationCache
    static class ChildClassToBeFixedCcOverUnsupportedCcBase extends UnsupportedCcBase {}

    @ToBeFixedForConfigurationCache
    static class ChildClassToBeFixedCcOverToBeFixedCcBase extends ToBeFixedCcBase {}

    // -- Two method-level intents of the same mode on one method --
    static class TwoMethodIntentsSameModeIp extends CleanBase {
        @ToBeFixedForIsolatedProjects
        @UnsupportedWithIsolatedProjects
        void featAnnotated() {}
    }

    static class TwoMethodIntentsSameModeCc extends CleanBase {
        @ToBeFixedForConfigurationCache
        @UnsupportedWithConfigurationCache
        void featAnnotated() {}
    }
}
