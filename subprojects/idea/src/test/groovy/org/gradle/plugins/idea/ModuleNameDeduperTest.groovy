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

import spock.lang.Specification

import org.gradle.plugins.idea.deduper.ModuleNameDeduper
import org.gradle.plugins.idea.deduper.ModuleNameDeduper.ModuleName

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class ModuleNameDeduperTest extends Specification {

    ModuleNameDeduper deduper = new ModuleNameDeduper()

    ModuleName ferrari = new ModuleName(moduleName:"ferrari", candidateNames:[])
    ModuleName fiat = new ModuleName(moduleName:"fiat", candidateNames:[])

    ModuleName fiatTwo = new ModuleName(moduleName:"fiat", candidateNames:["parentOne-fiat"])
    ModuleName fiatThree = new ModuleName(moduleName:"fiat", candidateNames:["parentTwo-fiat"])

    ModuleName fiatFour = new ModuleName(moduleName:"fiat", candidateNames:["parentTwo-fiat", "parentThree-parentTwo-fiat"])

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
}
