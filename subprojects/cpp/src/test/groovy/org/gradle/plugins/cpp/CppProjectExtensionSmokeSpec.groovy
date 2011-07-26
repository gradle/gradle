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
import org.gradle.plugins.cpp.source.CppSourceSet

/**
 * Ensures the mechanics of the cpp extension are working.
 */
class CppProjectExtensionSmokeSpec extends CppProjectSpec {

    def setup() {
        applyPlugin()
    }

    def "cpp extension is available"() {
        expect:
        cpp instanceof CppProjectExtension

        and:
        cpp {

        }
    }

    def "source set container is available"() {
        expect:
        cpp.sourceSets instanceof NamedDomainObjectContainer

        and:
        cpp {
            sourceSets {
                main { }
            }
        }
        
        and:
        cpp.sourceSets.main instanceof CppSourceSet
    }

}