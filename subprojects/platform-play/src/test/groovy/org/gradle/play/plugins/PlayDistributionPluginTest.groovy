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

import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.copy.CopySpecInternal
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
import org.gradle.play.internal.toolchain.PlayToolChainInternal
import org.gradle.play.internal.toolchain.PlayToolProvider
import spock.lang.Specification

class PlayDistributionPluginTest extends Specification {
    def plugin = new PlayDistributionPlugin()

    def "adds default distribution for each binary" () {
        DomainObjectSet jarTasks1 = Stub(DomainObjectSet)
        DomainObjectSet jarTasks2 = Stub(DomainObjectSet)
        PlayApplicationBinarySpec bin1 = binary("bin1", jarTasks1)
        PlayApplicationBinarySpec bin2 = binary("bin2", jarTasks2)
        BinaryContainer binaryContainer = Stub(BinaryContainer) {
            withType(PlayApplicationBinarySpec.class) >> Stub(NamedDomainObjectSet) {
                iterator() >> [ bin1, bin2 ].iterator()
            }
        }

        CopySpec spec1 = Mock(CopySpec)
        CopySpec spec2 = Mock(CopySpec)
        def distributions = [ bin1: distribution(spec1, bin1), bin2: distribution(spec2, bin2) ]
        PlayDistributionContainer distributionContainer = Mock(PlayDistributionContainer) {
            findByName(_) >> { String name ->
                return distributions[name]
            }
        }
        PlayToolChainInternal playToolChain = Stub(PlayToolChainInternal) {
            select(_) >> Stub(PlayToolProvider)
        }

        when:
        plugin.configureDistributions(distributionContainer, binaryContainer, playToolChain)

        then:
        1 * distributionContainer.create("bin1") >> distributions["bin1"]
        1 * distributionContainer.create("bin2") >> distributions["bin2"]

        and:
        1 * spec1.from(jarTasks1)
        1 * spec2.from(jarTasks2)
    }

    def "adds distribution tasks for binary" () {
        File buildDir = new File("")
        PlayApplicationBinarySpecInternal binary = Stub(PlayApplicationBinarySpecInternal) {
            getName() >> "playBinary"
        }
        BinaryContainer binaryContainer = Stub(BinaryContainer) {
            withType(PlayApplicationBinarySpec.class) >> Stub(NamedDomainObjectSet) {
                iterator() >> [ binary ].iterator()
            }
        }
        CollectionBuilder tasks = Mock(CollectionBuilder)
        CopySpec spec = Stub(CopySpec)
        PlayDistributionContainer distributions = Mock(PlayDistributionContainer) {
            iterator() >> [ Mock(PlayDistribution) { getName() >> "playBinary"  } ].iterator()
        }
        PlayToolChainInternal playToolChain = Stub(PlayToolChainInternal) {
            select(_) >> Stub(PlayToolProvider)
        }

        when:
        plugin.createDistributionTasks(tasks, binaryContainer, buildDir, distributions, playToolChain)

        then:
        1 * tasks.create("createPlayBinaryStartScripts", CreateStartScripts, _)
        1 * tasks.create("createPlayBinaryDist", Zip, _)
    }

    def binary(String name, DomainObjectSet jarTasks) {
        return Stub(PlayApplicationBinarySpec) {
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
