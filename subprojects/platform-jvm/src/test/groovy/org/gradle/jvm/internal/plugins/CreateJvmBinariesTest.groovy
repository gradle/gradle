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

import org.gradle.api.Action
import org.gradle.internal.reflect.Instantiator
import org.gradle.jvm.JarBinarySpec
import org.gradle.jvm.JvmLibrarySpec
import org.gradle.jvm.internal.DefaultJvmLibrarySpec
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.jvm.platform.JavaPlatform
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.jvm.plugins.JvmComponentPlugin
import org.gradle.language.base.LanguageSourceSet
import org.gradle.model.ModelMap
import org.gradle.platform.base.internal.ComponentSpecIdentifier
import org.gradle.platform.base.PlatformBaseSpecification
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.PlatformResolvers

class CreateJvmBinariesTest extends PlatformBaseSpecification {
    def toolChain = Mock(JavaToolChainInternal)
    def rule = new JvmComponentPlugin.Rules()
    def platforms = Mock(PlatformResolvers)
    ModelMap<JarBinarySpec> binaries = Mock(ModelMap)
    def instantiator = Mock(Instantiator)

    def "adds a binary for each jvm library"() {
        def library = BaseComponentFixtures.create(JvmLibrarySpec, DefaultJvmLibrarySpec, componentId("jvmLibOne", ":project-path"))
        def platform = DefaultJavaPlatform.current()
        def source1 = sourceSet("ss1")
        def source2 = sourceSet("ss2")

        when:
        library.sources.put("ss1", source1)
        library.sources.put("ss2", source2)
        rule.createBinaries(binaries, platforms, library)

        then:
        1 * platforms.resolve(JavaPlatform, _) >> platform
        1 * binaries.create("jar", _ as Action)
        0 * _
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
