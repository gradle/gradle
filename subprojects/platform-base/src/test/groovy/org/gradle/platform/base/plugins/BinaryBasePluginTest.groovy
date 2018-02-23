/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base.plugins

import org.gradle.api.DefaultTask
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.PlatformBaseSpecification

class BinaryBasePluginTest extends PlatformBaseSpecification {
    def "applies component base plugin only"() {
        when:
        dsl {
            apply plugin: BinaryBasePlugin
        }

        then:
        project.pluginManager.pluginContainer.size() == 3
        project.pluginManager.pluginContainer.findPlugin(ComponentBasePlugin) != null
        project.pluginManager.pluginContainer.findPlugin(LifecycleBasePlugin) != null
    }

    def "registers BinarySpec"() {
        when:
        dsl {
            apply plugin: BinaryBasePlugin
            model {
                baseBinary(BinarySpec) {
                }
            }
        }

        then:
        realize("baseBinary") instanceof BinarySpec
    }

    def "adds a 'binaries' container to the project model"() {
        when:
        dsl {
            apply plugin: BinaryBasePlugin
        }

        then:
        realizeBinaries() != null
    }

    def "defines a build lifecycle task for each binary"() {
        when:
        dsl {
            apply plugin: BinaryBasePlugin
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

    def "adds each source set as binary's inputs"() {
        when:
        dsl {
            apply plugin: BinaryBasePlugin
            model {
                binaries {
                    bin1(BinarySpec) {
                        sources {
                            put("src1", Stub(LanguageSourceSet))
                            put("src2", Stub(LanguageSourceSet))
                        }
                    }
                    bin2(BinarySpec) {
                        sources {
                            put("src1", Stub(LanguageSourceSet))
                        }
                    }
                }
            }
        }

        then:
        def binaries = realizeBinaries()
        binaries.size() == 2
        binaries.bin1.inputs == [binaries.bin1.sources.src1, binaries.bin1.sources.src2] as Set
        binaries.bin2.inputs == [binaries.bin2.sources.src1] as Set
    }

    def "copies binary tasks into task container"() {
        when:
        dsl {
            apply plugin: BinaryBasePlugin
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
