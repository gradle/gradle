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
package org.gradle.api.internal

import spock.lang.*

import org.gradle.api.Action;
import org.gradle.api.specs.Spec;

class FilteredActionSpec extends Specification {

    protected Spec s(Closure spec) {
        spec as Spec
    }

    protected a(Closure action) {
        action as Action
    }

    protected fa(Spec spec, Action action) {
        new FilteredAction(spec, action)
    }
    
    def "filtered action fires for matching"() {
        given:
        def called = false
        def spec = s { true }
        def action = fa(spec, a { called = true })
        
        expect:
        spec.isSatisfiedBy "object"
        
        when:
        action.execute "object"
        
        then:
        called == true
    }

    def "filtered action doesnt fire for not matching"() {
        given:
        def called = false
        def spec = s { false }
        def action = fa(spec, a { called = true })
        
        expect:
        !spec.isSatisfiedBy("object")
        
        when:
        action.execute "object"
        
        then:
        called == false
    }
    
}