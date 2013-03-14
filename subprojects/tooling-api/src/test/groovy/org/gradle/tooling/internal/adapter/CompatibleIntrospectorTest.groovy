/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.adapter

import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 3/16/12
 */
class CompatibleIntrospectorTest extends Specification {
    
    class Foo {
        
        int number
        
        String getMessage() {
            "Hello!"
        }
        
        void setNumber(int number) {
            this.number = number
        }
    }

    def foo = new Foo()
    def intro = new CompatibleIntrospector(foo)
    
    def "gets stuff safely"() {
        expect:
        'Hello!' == intro.getSafely('blah', 'getMessage')
        'blah' == intro.getSafely('blah', 'doesNotExist')
    }

    def "calls methods safely"() {
        when:
        intro.callSafely('doesNotExist', 10)
        then:
        foo.number == 0

        when:
        intro.callSafely('setNumber', 10)
        then:
        foo.number == 10
    }
}
