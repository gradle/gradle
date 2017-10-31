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

package org.gradle.binarycompatibility.rules

import japicmp.model.JApiCompatibilityChange

import me.champeau.gradle.japicmp.report.Violation
import japicmp.model.JApiClass
import javassist.CtClass
import com.google.common.base.Optional


class MethodsRemovedInInternalSuperClassRuleTest extends AbstractContextAwareRuleSpecification {
    MethodsRemovedInInternalSuperClassRule rule = new MethodsRemovedInInternalSuperClassRule([:])

    JApiClass apiClass = Stub(JApiClass)

    static class OldSuperInternal {
        public void publicMethod() {}

        private void privateMethod() {}
    }

    static class OldBase extends OldSuperInternal {}

    static class OldSub extends OldBase {}

    static class NewSuperInternal {}

    static class NewBase extends NewSuperInternal {}

    static class NewSub extends NewBase {}

    Map classes = [:]

    def setup() {
        rule.context = context
        [OldSuperInternal, NewSuperInternal].each {
            CtClass c = instanceScopedPool.get(it.name)
            c.name = 'test.internal.' + c.name
            classes[it.simpleName] = c
        }
        [OldBase, OldSub, NewBase, NewSub].each {
            classes[it.simpleName] = instanceScopedPool.get(it.name)
        }

        classes['OldBase'].superclass = classes['OldSuperInternal']
        classes['NewBase'].superclass = classes['NewSuperInternal']
    }

    def "method removal can be reported if current class is top"() {
        given:
        apiClass.oldClass >> Optional.of(classes['OldBase'])
        apiClass.newClass >> Optional.of(classes['NewBase'])
        apiClass.compatibilityChanges >> [JApiCompatibilityChange.METHOD_REMOVED_IN_SUPERCLASS]

        when:
        Violation violation = rule.maybeViolation(apiClass)

        then:
        violation.humanExplanation.contains('SuperInternal.publicMethod()')
        !violation.humanExplanation.contains('SuperInternal.privateMethod()')
    }

    def "method removal would not be reported if current class is not top"() {
        given:
        apiClass.oldClass >> Optional.of(classes['OldSub'])
        apiClass.newClass >> Optional.of(classes['NewSub'])
        apiClass.compatibilityChanges >> [JApiCompatibilityChange.METHOD_REMOVED_IN_SUPERCLASS]

        expect:
        rule.maybeViolation(apiClass) == null
    }
}
