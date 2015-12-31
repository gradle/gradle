/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base.plugins

import org.gradle.api.DefaultTask
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.PlatformBaseSpecification

class LanguageBasePluginTest extends PlatformBaseSpecification {
    def "adds a 'binaries' container to the project model"() {
        when:
        dsl {
            apply plugin: LanguageBasePlugin
        }

        then:
        realizeBinaries() != null
    }

    def "adds a 'sources' container to the project model"() {
        when:
        dsl {
            apply plugin: LanguageBasePlugin
        }

        then:
        realizeSourceSets() != null
    }

    def "defines a build lifecycle task for each binary"() {
        when:
        dsl {
            apply plugin: LanguageBasePlugin
            model {
                binaries {
                    bin1(BinarySpec)
                    bin2(BinarySpec)
                }
            }
        }

        then:
        def binaries = realizeBinaries()
        binaries.size() == 2
        binaries.bin1.buildTask instanceof DefaultTask
        binaries.bin1.buildTask.name == 'bin1'
        binaries.bin2.buildTask instanceof DefaultTask
        binaries.bin2.buildTask.name == 'bin2'
    }

    def "copies binary tasks into task container"() {
        when:
        dsl {
            apply plugin: LanguageBasePlugin
            model {
                binaries {
                    bin1(BinarySpec)
                    bin2(BinarySpec)
                }
            }
        }

        then:
        def tasks = realizeTasks()
        def binaries = realizeBinaries()
        tasks.bin1 == binaries.bin1.buildTask
        tasks.bin2 == binaries.bin2.buildTask
    }
}
