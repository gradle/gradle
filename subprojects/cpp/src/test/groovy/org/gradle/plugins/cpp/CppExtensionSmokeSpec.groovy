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
package org.gradle.plugins.cpp

import org.gradle.api.NamedDomainObjectContainer

/**
 * Ensures the mechanics of the nativ extension are working.
 */
class CppExtensionSmokeSpec extends CppProjectSpec {

    def setup() {
        applyPlugin()
    }

    def "extensions are available"() {
        expect:
        cpp instanceof CppExtension
        binaries instanceof NamedDomainObjectContainer
    }

    def "can create some cpp source sets"() {
        when:
        cpp {
            sourceSets {
                s1 {}
                s2 {}
            }
        }
        
        then:
        cpp.sourceSets.size() == 3 // implicit main
        cpp.sourceSets*.name == ["main", "s1", "s2"]
        cpp.sourceSets.s1 instanceof CppSourceSet
    }
    
    def "can create some binaries"() {
        when:
        binaries {
            b1 {}
            b2 {}
        }
        
        then:
        binaries.size() == 3 // implicit main
        binaries*.name == ["b1", "b2", "main"]
    }

}