/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.idea

import org.gradle.plugins.idea.configurer.ModuleNameDeduper
import spock.lang.Specification

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class ModuleNameDeduperTest extends Specification {

    public static class ModuleStub {
        String moduleName
        Collection<String> candidateNames
    }

    ModuleNameDeduper deduper = new ModuleNameDeduper()

    ModuleStub ferrari = new ModuleStub(moduleName:"ferrari", candidateNames:[])
    ModuleStub fiat = new ModuleStub(moduleName:"fiat", candidateNames:[])

    ModuleStub fiatTwo = new ModuleStub(moduleName:"fiat", candidateNames:["parentOne-fiat"])
    ModuleStub fiatThree = new ModuleStub(moduleName:"fiat", candidateNames:["parentTwo-fiat"])

    ModuleStub fiatFour = new ModuleStub(moduleName:"fiat", candidateNames:["parentTwo-fiat", "parentThree-parentTwo-fiat"])

    def "does nothing when no duplicates"() {
        given:
        def dedupeMe = [ferrari, fiat]

        when:
        deduper.dedupeModuleNames(dedupeMe)

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
        deduper.dedupeModuleNames(dedupeMe)

        then:
        ferrari.moduleName == "ferrari"
        fiat.moduleName == "fiat"
        fiatTwo.moduleName == "parentOne-fiat"
    }

    def "should dedupe module names even if first candidates are dupes"() {
        given:
        def dedupeMe = [ferrari, fiat, fiatTwo, fiatThree, fiatFour]

        when:
        deduper.dedupeModuleNames(dedupeMe)

        then:
        ferrari.moduleName == "ferrari"
        fiat.moduleName == "fiat"
        fiatTwo.moduleName == "parentOne-fiat"
        fiatThree.moduleName == "parentTwo-fiat"
        fiatFour.moduleName == "parentThree-parentTwo-fiat"
    }

    def "should deal with exactly the same module names"() {
        given:
        def module = new ModuleStub(moduleName: 'services', candidateNames: [])
        //module with the same name is theoretically possible if the user overrides the module name
        def module2 = new ModuleStub(moduleName: 'services', candidateNames: ['root-services'])
        def module3 = new ModuleStub(moduleName: 'services', candidateNames: ['root-services'])
        def module4 = new ModuleStub(moduleName: 'services', candidateNames: ['root-services'])

        when:
        deduper.dedupeModuleNames([module, module2, module3, module4])

        then:
        module.moduleName == "services"
        //if the user hardcoded module name to awkward values we are not deduping it...
        module2.moduleName == "root-services"
        module3.moduleName == "services"
        module4.moduleName == "services"
    }
}
