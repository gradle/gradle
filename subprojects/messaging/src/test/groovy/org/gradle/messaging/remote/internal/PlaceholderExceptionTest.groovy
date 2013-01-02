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
package org.gradle.messaging.remote.internal

import spock.lang.Specification
import spock.lang.Issue

@Issue("GRADLE-1905")
class PlaceholderExceptionTest extends Specification {
    def "toString() generally produces same output as original exception"() {
        def original = new Exception("original exception")
        def placeholder = new PlaceholderException(original.getClass().name, original.message, original.toString(), null, original.cause)
        
        expect:
        placeholder.toString() == original.toString()
    }
    
    def "toString() produces same output as original exception if the latter overrides toString()"() {
        def original = new Exception("original exception") {
            String toString() {
                "fancy customized toString"
            }
        }
        def placeholder = new PlaceholderException(original.getClass().name, original.message, original.toString(), null, original.cause)

        expect:
        placeholder.toString() == original.toString()
    }
}
