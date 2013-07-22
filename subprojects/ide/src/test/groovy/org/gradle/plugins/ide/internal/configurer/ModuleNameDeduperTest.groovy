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

package org.gradle.plugins.ide.internal.configurer

import spock.lang.Specification

class ModuleNameDeduperTest extends Specification {

    public static class TargetStub extends DeduplicationTarget {
        String moduleName
        Collection<String> candidateNames
        Closure updateModuleName = { moduleName = it }
    }

    ModuleNameDeduper deduper = new ModuleNameDeduper()

    TargetStub ferrari = new TargetStub(moduleName: "ferrari", candidateNames: [])
    TargetStub fiat = new TargetStub(moduleName: "fiat", candidateNames: [])

    TargetStub fiatTwo = new TargetStub(moduleName: "fiat", candidateNames: ["parentOne-fiat"])
    TargetStub fiatThree = new TargetStub(moduleName: "fiat", candidateNames: ["parentTwo-fiat"])

    TargetStub fiatFour = new TargetStub(moduleName: "fiat", candidateNames: ["parentTwo-fiat", "parentThree-parentTwo-fiat"])

    def "does nothing when no duplicates"() {
        given:
        def dedupeMe = [ferrari, fiat]

        when:
        deduper.dedupe(dedupeMe)

        then:
        dedupeMe == [ferrari, fiat]
        ferrari.moduleName == "ferrari"
        fiat.moduleName == "fiat"
    }

    def "should dedupe module names"() {
        given:
        def dedupeMe = [ferrari, fiat, fiatTwo]
        assert fiatTwo.moduleName == "fiat"
        assert fiat.moduleName == "fiat"

        when:
        deduper.dedupe(dedupeMe)

        then:
        ferrari.moduleName == "ferrari"
        fiat.moduleName == "fiat"
        fiatTwo.moduleName == "parentOne-fiat"
    }

    def "should dedupe module names even if first candidates are dupes"() {
        given:
        def dedupeMe = [ferrari, fiat, fiatTwo, fiatThree, fiatFour]

        when:
        deduper.dedupe(dedupeMe)

        then:
        ferrari.moduleName == "ferrari"
        fiat.moduleName == "fiat"
        fiatTwo.moduleName == "parentOne-fiat"
        fiatThree.moduleName == "parentTwo-fiat"
        fiatFour.moduleName == "parentThree-parentTwo-fiat"
    }

    def "should deal with exactly the same module names"() {
        given:
        def module = new TargetStub(moduleName: 'services', candidateNames: [])
        //module with the same name is theoretically possible if the user overrides the module name
        def module2 = new TargetStub(moduleName: 'services', candidateNames: ['root-services'])
        def module3 = new TargetStub(moduleName: 'services', candidateNames: ['root-services'])
        def module4 = new TargetStub(moduleName: 'services', candidateNames: ['root-services'])

        when:
        deduper.dedupe([module, module2, module3, module4])

        then:
        module.moduleName == "services"
        //if the user hardcoded module name to awkward values we are not deduping it...
        module2.moduleName == "root-services"
        module3.moduleName == "services"
        module4.moduleName == "services"
    }
}
