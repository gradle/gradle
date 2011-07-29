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
package org.gradle.plugins.nativ

class SourceSetConfigurationSpec extends NativeProjectSpec {

    def setup() {
        applyPlugin()
    }

    def "configure source sets"() {
        when:
        nativ {
            sourceSets {
                ss1 {
                    cpp {
                        srcDirs "d1", "d2"
                    }
                    headers {
                        srcDirs "h1", "h2"
                    }
                }
                ss2 {
                    cpp {
                        srcDirs "d3"
                    }
                    headers {
                        srcDirs "h3"
                    }
                }
            }
        }

        then:
        def ss1 = nativ.sourceSets.ss1
        def ss2 = nativ.sourceSets.ss2

        // cpp dir automatically added by convention
        ss1.cpp.srcDirs*.name == ["d1", "d2"]
        ss2.cpp.srcDirs*.name == ["d3"]

        // headers dir automatically added by convention
        ss1.headers.srcDirs*.name == ["h1", "h2"]
        ss2.headers.srcDirs*.name == ["h3"]
    }
}