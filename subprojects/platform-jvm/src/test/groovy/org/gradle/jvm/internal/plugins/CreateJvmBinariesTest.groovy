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

package org.gradle.jvm.internal.plugins

import org.gradle.jvm.platform.JvmPlatform
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.ComponentSpecIdentifier
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder
import org.gradle.jvm.JvmLibrarySpec
import org.gradle.jvm.internal.DefaultJarBinarySpec
import org.gradle.jvm.internal.DefaultJvmLibrarySpec
import org.gradle.jvm.plugins.JvmComponentPlugin
import org.gradle.jvm.toolchain.JavaToolChain
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toNamedDomainObjectSet

class CreateJvmBinariesTest extends Specification {
    def buildDir = new File("buildDir")
    def namingSchemeBuilder = Mock(BinaryNamingSchemeBuilder)
    def toolChain = Mock(JavaToolChain)
    def rule = new JvmComponentPlugin.Rules()
    def binaries = Mock(BinaryContainer)
    def mainSourceSet = Mock(FunctionalSourceSet)

    def serviceRegistry = ServiceRegistryBuilder.builder().provider(new Object() {
        JavaToolChain createToolChain() {
            toolChain
        }
    }).build()

    def "adds a binary for each jvm library"() {
        def library = new DefaultJvmLibrarySpec(componentId("jvmLibOne", ":project-path"), mainSourceSet)
        def namingScheme = Mock(BinaryNamingScheme)

        when:
        rule.createBinaries(binaries, namingSchemeBuilder, toNamedDomainObjectSet(JvmLibrarySpec, library), buildDir, serviceRegistry)

        then:
        _ * namingScheme.description >> "jvmLibJar"
        _ * namingScheme.outputDirectoryBase >> "jvmJarOutput"
        1 * namingSchemeBuilder.withComponentName("jvmLibOne") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withTypeString("jar") >> namingSchemeBuilder
        1 * namingSchemeBuilder.build() >> namingScheme
        1 * toolChain.assertValidPlatform(_ as JvmPlatform)
        1 * binaries.add({ DefaultJarBinarySpec binary ->
            binary.namingScheme == namingScheme
            binary.library == library
            binary.classesDir == new File(buildDir, "jvmJarOutput")
            binary.resourcesDir == binary.classesDir
            binary.toolChain == toolChain
        } as BinarySpec)
        0 * _
    }

    def "created binary has sources from jvm library"() {
        def library = new DefaultJvmLibrarySpec(componentId("jvmLibOne", ":project-path"), mainSourceSet)
        def namingScheme = Mock(BinaryNamingScheme)
        def source1 = Mock(LanguageSourceSet)
        def source2 = Mock(LanguageSourceSet)

        when:
        library.source([source1, source2])
        rule.createBinaries(binaries, namingSchemeBuilder, toNamedDomainObjectSet(JvmLibrarySpec, library), buildDir, serviceRegistry)

        then:
        _ * namingScheme.description >> "jvmLibJar"
        _ * namingScheme.outputDirectoryBase >> "jvmJarOutput"
        1 * namingSchemeBuilder.withComponentName("jvmLibOne") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withTypeString("jar") >> namingSchemeBuilder
        1 * namingSchemeBuilder.build() >> namingScheme
        1 * toolChain.assertValidPlatform(_ as JvmPlatform)
        1 * binaries.add({ DefaultJarBinarySpec binary ->
            binary.namingScheme == namingScheme
            binary.library == library
            binary.source == library.source
            binary.toolChain == toolChain
        } as BinarySpec)
        0 * _
    }

    def componentId(def name, def path) {
        Stub(ComponentSpecIdentifier) {
            getName() >> name
            getProjectPath() >> path
        }
    }
}
