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
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.DefaultNamedDomainObjectSet
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.java.archives.Manifest
import org.gradle.api.provider.Property
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.tasks.Jar
import org.gradle.model.ModelMap
import org.gradle.platform.base.BinaryTasksCollection
import org.gradle.play.PlayApplicationBinarySpec
import org.gradle.play.distribution.PlayDistribution
import org.gradle.play.distribution.PlayDistributionContainer
import org.gradle.play.internal.DefaultPlayPlatform
import org.gradle.play.internal.PlayApplicationBinarySpecInternal
import org.gradle.play.platform.PlayPlatform
import org.gradle.util.CollectionUtils
import org.gradle.util.TestUtil
import spock.lang.Specification

class PlayDistributionPluginTest extends Specification {
    def plugin = new PlayDistributionPlugin()

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
        1 * distributions.withType(PlayDistribution) >> toNamedDomainObjectSet(PlayDistribution, distribution)
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
                1 * getArchiveFileName() >> Mock(Property)
                1 * dependsOn(jarTasks)
                1 * getDestinationDirectory() >> Mock(DirectoryProperty)
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

    /**
     * Wraps the given items in a named domain object set.
     */
    static <T extends Named> NamedDomainObjectSet<T> toNamedDomainObjectSet(Class<T> type, T... items) {
        DefaultNamedDomainObjectSet<T> domainObjectSet = new DefaultNamedDomainObjectSet<T>(type, TestUtil.instantiatorFactory().decorateLenient())
        CollectionUtils.addAll(domainObjectSet, items)
        return domainObjectSet
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
