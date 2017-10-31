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

import spock.lang.Unroll
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiMethod
import japicmp.model.JApiImplementedInterface
import japicmp.model.JApiChangeStatus
import org.gradle.api.Incubating
import me.champeau.gradle.japicmp.report.Violation
import japicmp.model.JApiClass
import javassist.CtClass

import com.google.common.base.Optional

class IncubatingInternalInterfaceAddedRuleTest extends AbstractContextAwareRuleSpecification {
    IncubatingInternalInterfaceAddedRule rule = new IncubatingInternalInterfaceAddedRule([:])

    static class OldSuper {}

    static class OldBase extends OldSuper {}

    static class NewSuper {}

    static class NewBase extends NewSuper {}

    @Incubating
    static class NewIncubatingBase extends NewSuper {}

    @Incubating
    interface IncubatingInterface {}

    interface InternalInterface {}

    interface UnusedInterface {}

    CtClass oldBase
    CtClass newBase
    CtClass newSuper
    CtClass newIncubatingBase
    CtClass internalInterface
    CtClass incubatingInterface
    CtClass unusedInterface

    JApiClass apiClass = Stub(JApiClass)

    Map interfaces

    def setup() {
        rule.context = context

        oldBase = instanceScopedPool.get(OldBase.name)
        newBase = instanceScopedPool.get(NewBase.name)
        newSuper = instanceScopedPool.get(NewSuper.name)
        newIncubatingBase = instanceScopedPool.get(NewIncubatingBase.name)
        internalInterface = instanceScopedPool.get(InternalInterface.name)
        incubatingInterface = instanceScopedPool.get(IncubatingInterface.name)
        unusedInterface = instanceScopedPool.get(UnusedInterface.name)

        apiClass.changeStatus >> JApiChangeStatus.MODIFIED
        apiClass.oldClass >> Optional.of(oldBase)
        apiClass.newClass >> Optional.of(newBase)

        internalInterface.name = "test.internal." + internalInterface.name

        interfaces = ['internal': internalInterface,
                      'incubating': incubatingInterface]
    }

    @Unroll
    def "#member change should not be reported"() {
        expect:
        rule.maybeViolation(apiClass) == null

        where:
        member << [Mock(JApiMethod), Mock(JApiField), Mock(JApiImplementedInterface), Mock(JApiConstructor)]
    }

    def "nothing be reported if no changes"() {
        expect:
        rule.maybeViolation(apiClass) == null
    }

    def "nothing be reported if new interface is neither internal nor incubating"() {
        when:
        newBase.addInterface(unusedInterface)

        then:
        rule.maybeViolation(apiClass) == null
    }

    def 'adding an #type interface can be reported'() {
        given:
        newBase.addInterface(interfaces[type])

        when:
        Violation violation = rule.maybeViolation(apiClass)

        then:
        violation.humanExplanation.contains(result)

        where:
        type         | result
        'internal'   | "\"test.internal.${InternalInterface.name}\""
        'incubating' | "\"${IncubatingInterface.name}\""
    }

    def "do not report if they are both incubating"() {
        given:
        apiClass.newClass >> Optional.of(newIncubatingBase)
        newSuper.addInterface(incubatingInterface)

        expect:
        rule.maybeViolation(apiClass) == null
    }

    def "adding an #type interface to super class would not be reported"() {
        given:
        newSuper.addInterface(interfaces[type])

        expect:
        rule.maybeViolation(apiClass) == null

        where:
        type << ['internal', 'incubating']
    }
}
