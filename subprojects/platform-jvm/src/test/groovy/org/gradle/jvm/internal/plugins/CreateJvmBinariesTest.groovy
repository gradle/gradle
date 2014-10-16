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
import org.gradle.api.JavaVersion
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.jvm.JvmLibraryBinarySpec
import org.gradle.jvm.JvmLibrarySpec
import org.gradle.jvm.internal.DefaultJarBinarySpec
import org.gradle.jvm.internal.DefaultJvmLibrarySpec
import org.gradle.jvm.internal.JarBinarySpecInternal
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.jvm.platform.JavaPlatform
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.jvm.plugins.JvmComponentPlugin
import org.gradle.jvm.toolchain.JavaToolChainRegistry
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.ComponentSpecIdentifier
import org.gradle.platform.base.PlatformContainer
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder
import org.gradle.platform.base.internal.toolchain.ToolProvider
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toDomainObjectSet
import static org.gradle.util.WrapUtil.toNamedDomainObjectSet

class CreateJvmBinariesTest extends Specification {
    def buildDir = new File("buildDir")
    def namingSchemeBuilder = Mock(BinaryNamingSchemeBuilder)
    def toolChain = Mock(JavaToolChainInternal)
    def toolProvider = Mock(ToolProvider)
    def rule = new JvmComponentPlugin.Rules()
    def platforms = Mock(PlatformContainer)
    def binaries = Mock(BinaryContainer)
    def instantiator = Mock(Instantiator)
    def mainSourceSet = new DefaultFunctionalSourceSet("ss", new DirectInstantiator())
    def toolChainRegistry = Mock(JavaToolChainRegistry)

    def serviceRegistry = ServiceRegistryBuilder.builder().provider(new Object() {
        Instantiator createInstantiator() {
            instantiator
        }

    }).build()

    def "adds a binary for each jvm library"() {
        def library = new DefaultJvmLibrarySpec(componentId("jvmLibOne", ":project-path"), mainSourceSet)
        def namingScheme = Mock(BinaryNamingScheme)
        def platform = new DefaultJavaPlatform("test")
        platform.setTargetCompatibility(JavaVersion.current())
        def binary = new DefaultJarBinarySpec(library, namingScheme, toolChain, platform)
        def source1 = sourceSet("ss1")
        def source2 = sourceSet("ss2")

        when:
        library.sources.addAll([source1, source2])
        rule.createBinaries(binaries, platforms, namingSchemeBuilder, toNamedDomainObjectSet(JvmLibrarySpec, library), buildDir, serviceRegistry, toolChainRegistry)

        then:
        _ * namingScheme.description >> "jvmLibJar"
        _ * namingScheme.outputDirectoryBase >> "jvmJarOutput"
        1 * platforms.chooseFromTargets(JavaPlatform, _, _, _) >> [ platform ]
        1 * toolChainRegistry.getForPlatform(platform) >> toolChain
        1 * namingSchemeBuilder.withComponentName("jvmLibOne") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withTypeString("jar") >> namingSchemeBuilder
        1 * namingSchemeBuilder.build() >> namingScheme
        1 * instantiator.newInstance(DefaultJarBinarySpec.class, library, namingScheme, toolChain, platform) >> binary
        1 * toolChain.select(platform) >> toolProvider
        1 * toolProvider.isAvailable() >> true
        1 * binaries.addAll(toDomainObjectSet(JvmLibraryBinarySpec, binary))
        0 * _

        and:
        JarBinarySpecInternal createdBinary = library.getBinaries().iterator().next()
        createdBinary.namingScheme == namingScheme
        createdBinary.library == library
        createdBinary.classesDir == new File(buildDir, "classes/jvmJarOutput")
        createdBinary.resourcesDir == binary.classesDir
        createdBinary.toolChain == toolChain
        createdBinary.source as Set == [source1, source2] as Set
    }

    def componentId(def name, def path) {
        Stub(ComponentSpecIdentifier) {
            getName() >> name
            getProjectPath() >> path
        }
    }

    def sourceSet(def name) {
        Stub(LanguageSourceSet) {
            getName() >> name
        }
    }
}
