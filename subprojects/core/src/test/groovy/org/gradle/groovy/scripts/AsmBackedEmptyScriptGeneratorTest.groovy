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
package org.gradle.groovy.scripts

import spock.lang.Specification

class AsmBackedEmptyScriptGeneratorTest extends Specification {
    private final AsmBackedEmptyScriptGenerator generator = new AsmBackedEmptyScriptGenerator()

    def generatesEmptyScriptClass() {
        expect:
        def cl = generator.generate(groovy.lang.Script.class)
        def script = cl.newInstance()
        script instanceof groovy.lang.Script
        script.run() == null
    }
    
    def cachesScriptClass() {
        expect:
        def cl1 = generator.generate(groovy.lang.Script.class)
        def cl2 = generator.generate(groovy.lang.Script.class)
        cl1 == cl2
    }
}
