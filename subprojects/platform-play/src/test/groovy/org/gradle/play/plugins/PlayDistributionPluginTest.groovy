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

import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Zip
import org.gradle.model.collection.CollectionBuilder
import org.gradle.platform.base.BinaryContainer
import org.gradle.play.PlayApplicationBinarySpec
import org.gradle.play.internal.PlayApplicationBinarySpecInternal
import spock.lang.Specification

class PlayDistributionPluginTest extends Specification {
    def plugin = new PlayDistributionPlugin()

    def "adds distribution for each binary" () {
        PlayApplicationBinarySpec bin1 = binary("bin1")
        PlayApplicationBinarySpec bin2 = binary("bin2")
        BinaryContainer binaryContainer = Stub(BinaryContainer) {
            withType(PlayApplicationBinarySpec.class) >> Stub(NamedDomainObjectSet) {
                iterator() >> [ bin1, bin2 ].iterator()
            }
        }

        CopySpec spec1 = Mock(CopySpec)
        CopySpec spec2 = Mock(CopySpec)
        def distributions = [ bin1: distribution(spec1), bin2: distribution(spec2) ]
        DistributionContainer distributionContainer = Mock(DistributionContainer) {
            findByName(_) >> { String name ->
                return distributions[name]
            }
        }

        when:
        plugin.configureDistributions(distributionContainer, binaryContainer)

        then:
        1 * distributionContainer.create("bin1") >> distributions["bin1"]
        1 * distributionContainer.create("bin2") >> distributions["bin2"]

        and:
        1 * spec1.from(bin1.getJarFile(), bin1.getAssetsJarFile())
        1 * spec2.from(bin2.getJarFile(), bin2.getAssetsJarFile())
    }

    def "adds distribution tasks for binary" () {
        File buildDir = new File("")
        PlayApplicationBinarySpecInternal binary = Stub(PlayApplicationBinarySpecInternal) {
            getName() >> "playBinary"
        }
        CollectionBuilder tasks = Mock(CollectionBuilder)
        CopySpec spec = Stub(CopySpec)
        DistributionContainer distributions = Mock(DistributionContainer) {
            findByName(_) >> distribution(spec)
        }

        when:
        plugin.createDistributionTasks(tasks, binary, buildDir, distributions)

        then:
        1 * tasks.create("createPlayBinaryStartScripts", CreateStartScripts, _)
        1 * tasks.create("createPlayBinaryDist", Zip, _)
    }

    def binary(String name) {
        File jarFile = Stub(File)
        File assetsJarFile = Stub(File)
        return Stub(PlayApplicationBinarySpec) {
            getJarFile() >> jarFile
            getAssetsJarFile() >> assetsJarFile
            getName() >> name
        }
    }

    def distribution(CopySpec spec) {
        return Mock(Distribution) {
            getContents() >> Mock(CopySpecInternal) {
                addChild() >> Mock(CopySpecInternal) {
                    into(_) >> spec
                }
            }
        }
    }
}
