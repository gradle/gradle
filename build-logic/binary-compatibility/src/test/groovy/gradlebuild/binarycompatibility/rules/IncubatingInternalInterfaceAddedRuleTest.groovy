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

import japicmp.model.JApiChangeStatus
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiImplementedInterface
import japicmp.model.JApiMethod
import japicmp.util.Optional
import javassist.CtClass
import me.champeau.gradle.japicmp.report.Violation
import org.gradle.api.Incubating

class IncubatingInternalInterfaceAddedRuleTest extends AbstractContextAwareRuleSpecification {
    IncubatingInternalInterfaceAddedRule rule

    static class OldSuper {}

    static class OldBase extends OldSuper {}

    static class NewSuper {}

    static class NewBase extends NewSuper {}

    @Incubating
    static class NewIncubatingBase extends NewSuper {}

    @Incubating
    interface IncubatingInterface {}

    interface InternalInterface {}

    interface StablePublicInterface {}

    CtClass oldBase
    CtClass newBase
    CtClass newSuper
    CtClass newIncubatingBase
    CtClass internalInterface
    CtClass incubatingInterface
    CtClass stablePublicInterface

    Map interfaces

    def setup() {
        rule = new IncubatingInternalInterfaceAddedRule(getInitializationParams())
        rule.context = context

        oldBase = instanceScopedPool.get(OldBase.name)
        newBase = instanceScopedPool.get(NewBase.name)
        newSuper = instanceScopedPool.get(NewSuper.name)
        newIncubatingBase = instanceScopedPool.get(NewIncubatingBase.name)
        internalInterface = instanceScopedPool.get(InternalInterface.name)
        incubatingInterface = instanceScopedPool.get(IncubatingInterface.name)
        stablePublicInterface = instanceScopedPool.get(StablePublicInterface.name)

        apiClass.changeStatus >> JApiChangeStatus.MODIFIED
        apiClass.oldClass >> Optional.of(oldBase)
        apiClass.newClass >> Optional.of(newBase)

        internalInterface.name = replaceAsInternal(internalInterface.name)

        interfaces = ['internal': internalInterface,
                      'incubating': incubatingInterface]
    }

    def "#member change should not be reported"() {
        expect:
        noViolation(rule)

        where:
        member << [Mock(JApiMethod), Mock(JApiField), Mock(JApiImplementedInterface), Mock(JApiConstructor)]
    }

    def "nothing should be reported if no changes"() {
        expect:
        noViolation(rule)
    }

    def "nothing should be reported if new interface is neither internal nor incubating"() {
        when:
        newBase.addInterface(stablePublicInterface)

        then:
        noViolation(rule)
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
        'internal'   | "\"${replaceAsInternal(InternalInterface.name)}\""
        'incubating' | "\"${IncubatingInterface.name}\""
    }

    def "do not report if they are both incubating"() {
        given:
        apiClass.newClass >> Optional.of(newIncubatingBase)
        newSuper.addInterface(incubatingInterface)

        expect:
        noViolation(rule)
    }

    def "adding an #type interface to super class would not be reported"() {
        given:
        newSuper.addInterface(interfaces[type])

        expect:
        noViolation(rule)

        where:
        type << ['internal', 'incubating']
    }
}
