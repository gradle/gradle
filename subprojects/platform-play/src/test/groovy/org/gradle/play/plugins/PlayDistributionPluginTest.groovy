/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.plugins

import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DestinationRootCopySpec
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.gradle.model.collection.CollectionBuilder
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinaryTasksCollection
import org.gradle.play.PlayApplicationBinarySpec
import org.gradle.play.distribution.PlayDistribution
import org.gradle.play.distribution.PlayDistributionContainer
import org.gradle.play.internal.PlayApplicationBinarySpecInternal
import spock.lang.Specification

class PlayDistributionPluginTest extends Specification {
    def plugin = new PlayDistributionPlugin()

    def "adds default distribution for each binary" () {
        DomainObjectSet jarTasks1 = Stub(DomainObjectSet)
        DomainObjectSet jarTasks2 = Stub(DomainObjectSet)
        PlayApplicationBinarySpecInternal bin1 = binary("bin1", jarTasks1)
        PlayApplicationBinarySpecInternal bin2 = binary("bin2", jarTasks2)
        BinaryContainer binaryContainer = binaryContainer([ bin1, bin2 ])

        CopySpec spec1 = Mock(CopySpec)
        CopySpec spec2 = Mock(CopySpec)
        def distributions = [ bin1: distribution(spec1, bin1), bin2: distribution(spec2, bin2) ]
        PlayDistributionContainer distributionContainer = Mock(PlayDistributionContainer) {
            findByName(_) >> { String name ->
                return distributions[name]
            }
        }
        ConfigurationContainer configurationContainer = Stub(ConfigurationContainer) {
            create(_) >> Stub(Configuration)
            maybeCreate(_) >> Stub(Configuration)
        }
        PlayPluginConfigurations configurations = new PlayPluginConfigurations(configurationContainer, Stub(DependencyHandler))


        when:
        plugin.createDistributions(distributionContainer, binaryContainer, configurations)

        then:
        1 * distributionContainer.create("bin1") >> distributions["bin1"]
        1 * distributionContainer.create("bin2") >> distributions["bin2"]

        and:
        1 * spec1.from(jarTasks1)
        1 * spec2.from(jarTasks2)
    }

    def "adds scripts task for binary" () {
        File buildDir = new File("")
        PlayApplicationBinarySpecInternal binary = Stub(PlayApplicationBinarySpecInternal) {
            getName() >> "playBinary"
        }
        CollectionBuilder tasks = Mock(CollectionBuilder) {
            get("createPlayBinaryStartScripts") >>> [ null, Stub(CreateStartScripts) ]
        }
        PlayDistribution distribution = Mock(PlayDistribution) {
            getName() >> "playBinary"
            getBinary() >> binary
            getContents() >> Mock(CopySpecInternal) {
                addChild() >> Mock(CopySpecInternal) {
                    into("bin") >> Mock(CopySpec) {
                        1 * from(_ as CreateStartScripts)
                        1 * setFileMode(0755)
                    }
                }
            }
        }
        PlayDistributionContainer distributions = distributions([distribution])
        ConfigurationContainer configurationContainer = Stub(ConfigurationContainer) {
            create(_) >> Stub(Configuration)
            maybeCreate(_) >> Stub(Configuration)
        }
        PlayPluginConfigurations configurations = new PlayPluginConfigurations(configurationContainer, Stub(DependencyHandler))

        when:
        plugin.createStartScriptTasks(tasks, buildDir, distributions, configurations)

        then:
        1 * tasks.create("createPlayBinaryStartScripts", CreateStartScripts, _) >> { String name, Class type, Action action ->
            action.execute(Mock(CreateStartScripts) {
                1 * setDescription(_)
                1 * setClasspath(_)
                1 * setMainClassName("play.core.server.NettyServer")
                1 * setApplicationName("playBinary")
                1 * setOutputDir(_)
            })
        }
    }

    def "adds dist and stage tasks for binary" () {
        File buildDir = new File("")
        PlayApplicationBinarySpecInternal binary = Stub(PlayApplicationBinarySpecInternal) {
            getName() >> "playBinary"
        }
        CollectionBuilder tasks = Mock(CollectionBuilder) {
            get("stagePlayBinaryDist") >> Stub(Copy)
        }
        PlayDistribution distribution = Mock(PlayDistribution) {
            getName() >> "playBinary"
            getContents() >> Mock(CopySpecInternal)
        }
        PlayDistributionContainer distributions = distributions([ distribution ])

        when:
        plugin.createDistributionZipTasks(tasks, buildDir, distributions)

        then:
        1 * tasks.create("createPlayBinaryDist", Zip, _) >> { String name, Class type, Action action ->
            action.execute(Mock(Zip) {
                1 * setDescription(_)
                1 * setGroup(_)
                1 * setDestinationDir(_)
                1 * setArchiveName("playBinary.zip")
                1 * from(_ as Copy)
            })
        }
        1 * tasks.create("stagePlayBinaryDist", Copy, _) >> { String name, Class type, Action action ->
            action.execute(Mock(Copy) {
                1 * setDescription(_)
                1 * setGroup(_)
                1 * setDestinationDir(_)
                1 * getRootSpec() >> Mock(DestinationRootCopySpec) {
                    1 * addChild() >> Mock(CopySpecInternal) {
                        1 * into("playBinary")
                        1 * with(_ as CopySpecInternal)
                    }
                }
            })
        }
    }

    def binaryContainer(List binaries) {
        return Stub(BinaryContainer) {
            withType(PlayApplicationBinarySpecInternal.class) >> Stub(NamedDomainObjectSet) {
                iterator() >> binaries.iterator()
            }
        }
    }

    def distributions(List distributions) {
        return Stub(PlayDistributionContainer) {
            iterator() >> distributions.iterator()
            matching(_) >> Stub(NamedDomainObjectSet) {
                iterator() >> distributions.iterator()
            }
        }
    }

    def binary(String name, DomainObjectSet jarTasks) {
        return Stub(PlayApplicationBinarySpecInternal) {
            getTasks() >> Stub(BinaryTasksCollection) {
                withType(Jar.class) >> jarTasks
            }
            getName() >> name
        }
    }

    def distribution(CopySpec spec, PlayApplicationBinarySpec binary) {
        return Mock(PlayDistribution) {
            getContents() >> Mock(CopySpecInternal) {
                addChild() >>> [ libSpec(spec), confSpec() ]
                1 * from("README")
            }
            getBinary() >> binary
        }
    }

    def libSpec(CopySpec spec) {
        return Mock(CopySpecInternal) {
            1 * into("lib") >> spec
        }
    }

    def confSpec() {
        return Mock(CopySpecInternal) {
            1 * into("conf") >> Mock(CopySpec) {
                1 * from("conf") >> Mock(CopySpec) {
                    1 * exclude("routes")
                }
            }
        }
    }
}
