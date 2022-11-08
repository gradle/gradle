/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility.rules

import japicmp.model.JApiCompatibilityChange
import japicmp.util.Optional
import javassist.CtClass
import me.champeau.gradle.japicmp.report.Violation

class MethodsRemovedInInternalSuperClassRuleTest extends AbstractContextAwareRuleSpecification {
    MethodsRemovedInInternalSuperClassRule rule

    static class OldSuperInternal {
        void publicMethod() {}

        private void privateMethod() {}
    }

    static class OldBase extends OldSuperInternal {
        void anotherPublicMethod() {}
    }

    static class OldSub extends OldBase {}

    static class NewSuperInternal {}

    static class NewBase extends NewSuperInternal {}

    static class NewSub extends NewBase {}

    Map classes = [:]

    def setup() {
        rule = new MethodsRemovedInInternalSuperClassRule(getInitializationParams())
        rule.context = context
        [OldSuperInternal, NewSuperInternal].each {
            CtClass c = instanceScopedPool.get(it.name)
            c.name = replaceAsInternal(c.name)
            classes[it.simpleName] = c
        }
        [OldBase, OldSub, NewBase, NewSub].each {
            classes[it.simpleName] = instanceScopedPool.get(it.name)
        }

        classes['OldBase'].superclass = classes['OldSuperInternal']
        classes['NewBase'].superclass = classes['NewSuperInternal']
        classes['OldSub'].superclass = classes['OldBase']
        classes['NewSub'].superclass = classes['NewBase']

        apiClass.compatibilityChanges >> [JApiCompatibilityChange.METHOD_REMOVED_IN_SUPERCLASS]
    }

    def "method removal can be reported if current class is first public class"() {
        given:
        apiClass.oldClass >> Optional.of(classes['OldBase'])
        apiClass.newClass >> Optional.of(classes['NewBase'])

        when:
        Violation violation = rule.maybeViolation(apiClass)

        then:
        violation.humanExplanation.contains('SuperInternal.publicMethod()')
        !violation.humanExplanation.contains('SuperInternal.privateMethod()')
    }

    def "method removal would not be reported if current class is not first public class"() {
        given:
        apiClass.oldClass >> Optional.of(classes['OldSub'])
        apiClass.newClass >> Optional.of(classes['NewSub'])

        expect:
        noViolation(rule)
    }
}
