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
package org.gradle.initialization

import spock.lang.Specification

class CompositeInitScriptFinderTest extends Specification {
    final InitScriptFinder target1 = Mock()
    final InitScriptFinder target2 = Mock()
    final CompositeInitScriptFinder finder = new CompositeInitScriptFinder(target1, target2)
    
    def "collects up scripts from all finders"() {
        def result = []

        when:
        finder.findScripts(result)

        then:
        1 * target1.findScripts(result)
        1 * target2.findScripts(result)
    }
}
