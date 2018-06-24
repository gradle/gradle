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
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DestinationRootCopySpec
import org.gradle.api.java.archives.Manifest
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.jvm.tasks.Jar
import org.gradle.model.ModelMap
import org.gradle.platform.base.BinaryTasksCollection
import org.gradle.play.PlayApplicationBinarySpec
import org.gradle.play.distribution.PlayDistribution
import org.gradle.play.distribution.PlayDistributionContainer
import org.gradle.play.internal.DefaultPlayPlatform
import org.gradle.play.internal.PlayApplicationBinarySpecInternal
import org.gradle.play.internal.distribution.DefaultPlayDistribution
import org.gradle.play.platform.PlayPlatform
import org.gradle.util.WrapUtil
import spock.lang.Specification

class PlayDistributionPluginTest extends Specification {
    def plugin = new PlayDistributionPlugin()

    def "adds default distribution for each binary" () {
        DomainObjectSet jarTasks1 = Stub(DomainObjectSet)
        DomainObjectSet jarTasks2 = Stub(DomainObjectSet)
        PlayApplicationBinarySpec bin1 = binary("bin1", jarTasks1)
        PlayApplicationBinarySpec bin2 = binary("bin2", jarTasks2)
        ModelMap<PlayApplicationBinarySpec> binaryContainer = binaryContainer([ bin1, bin2 ])

        def distributions = [ bin1: distribution(bin1), bin2: distribution(bin2) ]
        PlayDistributionContainer distributionContainer = Mock(PlayDistributionContainer) {
            findByName(_) >> { String name ->
                return distributions[name]
            }
        }
        ServiceRegistry serviceRegistry = Mock(ServiceRegistry) {
            get(Instantiator.class) >> Mock(Instantiator) {
                newInstance(DefaultPlayDistribution.class, _, _, _) >> { Class c, otherArgs ->
                    return distributions[otherArgs[0]]
                }
            }
            get(FileOperations.class) >> Mock(FileOperations) {
                copySpec(_) >> Stub(CopySpec)
            }
        }
        ConfigurationContainer configurationContainer = Stub(ConfigurationContainer) {
            create(_) >> Stub(Configuration)
            maybeCreate(_) >> Stub(Configuration)
        }
        PlayPluginConfigurations configurations = new PlayPluginConfigurations(configurationContainer, Stub(DependencyHandler))

        when:
        plugin.createDistributions(distributionContainer, binaryContainer, configurations, serviceRegistry)

        then:
        1 * distributionContainer.add(distributions["bin1"])
        1 * distributionContainer.add(distributions["bin2"])
    }

    def "adds scripts and distribution jar tasks for binary" () {
        def distributions = Mock(PlayDistributionContainer)
        File buildDir = new File("")
        DomainObjectSet jarTasks = Stub(DomainObjectSet)
        PlayApplicationBinarySpec binary = binary("playBinary", jarTasks, playVersion)
        binary.getJarFile() >> Stub(File) {
            getName() >> "playBinary.zip"
        }
        ModelMap tasks = Mock(ModelMap) {
            get("createPlayBinaryStartScripts") >> Stub(CreateStartScripts)
            get("createPlayBinaryDistributionJar") >> Stub(Jar)
        }
        PlayDistribution distribution = Mock(PlayDistribution) {
            getName() >> "playBinary"
            getBinary() >> binary
            getContents() >> Mock(CopySpecInternal) {
                1 * from("README")
                addChild() >> Mock(CopySpecInternal) {
                    into("lib") >> Mock(CopySpec) {
                        1 * from(_ as Jar)
                        1 * from(_ as File)
                        1 * from(_ as FileCollection)
                    }
                    into("bin") >> Mock(CopySpec) {
                        1 * from(_ as CreateStartScripts)
                        1 * setFileMode(0755)
                    }
                    into("conf") >> Mock(CopySpec) {
                        1 * from("conf") >> Mock(CopySpec) {
                            1 * exclude("routes")
                        }
                    }
                }
            }
        }
        ConfigurationContainer configurationContainer = Stub(ConfigurationContainer) {
            create(_) >> Stub(Configuration)
            maybeCreate(_) >> Stub(Configuration)
        }
        PlayPluginConfigurations configurations = new PlayPluginConfigurations(configurationContainer, Stub(DependencyHandler))

        when:
        plugin.createDistributionContentTasks(tasks, buildDir, distributions, configurations)

        then:
        1 * distributions.withType(PlayDistribution) >> WrapUtil.toNamedDomainObjectSet(PlayDistribution, distribution)
        1 * tasks.create("createPlayBinaryStartScripts", CreateStartScripts, _) >> { String name, Class type, Action action ->
            action.execute(Mock(CreateStartScripts) {
                1 * setDescription(_)
                1 * setClasspath(_)
                1 * setMainClassName(mainClass)
                1 * setApplicationName("playBinary")
                1 * setOutputDir(_)
            })
        }
        1 * tasks.create("createPlayBinaryDistributionJar", Jar, _) >> { String name, Class type, Action action ->
            action.execute(Mock(Jar) {
                1 * setArchiveName("playBinary.zip")
                1 * dependsOn(jarTasks)
                1 * setDestinationDir(_)
                1 * from(_ as FileTree)
                1 * getProject() >> Stub(Project) {
                    fileTree(_) >> Stub(FileTree)
                }
                1 * getManifest() >> Mock(Manifest) {
                    1 * attributes(_) >> { Map attributes ->
                        assert attributes.containsKey("Class-Path")
                    }
                }
            })
        }

        where:
        playVersion | mainClass
        "2.4.11"    | "play.core.server.ProdServerStart"
        "2.5.18"    | "play.core.server.ProdServerStart"
        "2.6.6"     | "play.core.server.ProdServerStart"
    }

    def "adds dist and stage tasks for binary" () {
        File buildDir = new File("")
        DomainObjectSet jarTasks = Stub(DomainObjectSet)
        PlayApplicationBinarySpec binary = binary("playBinary", jarTasks)
        ModelMap tasks = Mock(ModelMap) {
            get("stagePlayBinaryDist") >> Stub(Sync)
        }
        PlayDistribution distribution = Mock(PlayDistribution) {
            getName() >> "playBinary"
            getContents() >> Mock(CopySpecInternal)
        }
        def distributions = Mock(PlayDistributionContainer)

        when:
        plugin.createDistributionZipTasks(tasks, buildDir, distributions)

        then:
        1 * distributions.withType(PlayDistribution) >> WrapUtil.toNamedDomainObjectSet(PlayDistribution, distribution)
        1 * tasks.create("createPlayBinaryZipDist", Zip, _) >> { String name, Class type, Action action ->
            action.execute(Mock(Zip) {
                1 * setDescription(_)
                1 * setDestinationDir(_)
                1 * setBaseName("playBinary")
                1 * from(_ as Sync)
            })
        }
        1 * tasks.create("createPlayBinaryTarDist", Tar, _) >> { String name, Class type, Action action ->
            action.execute(Mock(Tar) {
                1 * setDescription(_)
                1 * setDestinationDir(_)
                1 * setBaseName("playBinary")
                1 * from(_ as Sync)
            })
        }
        1 * tasks.create("stagePlayBinaryDist", Sync, _) >> { String name, Class type, Action action ->
            action.execute(Mock(Sync) {
                1 * setDescription(_)
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
        return Stub(ModelMap) {
            iterator() >> binaries.iterator()
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

    def binary(String name, DomainObjectSet jarTasks, String playVersion = DefaultPlayPlatform.DEFAULT_PLAY_VERSION) {
        return Stub(PlayApplicationBinarySpecInternal) {
            getTasks() >> Stub(BinaryTasksCollection) {
                withType(Jar.class) >> jarTasks
            }
            getName() >> name
            getProjectScopedName() >> name
            getTargetPlatform() >> platform(playVersion)
        }
    }

    def platform(String playVersion) {
        return Stub(PlayPlatform) {
            getPlayVersion() >> playVersion
        }
    }

    def distribution(PlayApplicationBinarySpec binary) {
        return Mock(PlayDistribution) {
            getBinary() >> binary
        }
    }
}
