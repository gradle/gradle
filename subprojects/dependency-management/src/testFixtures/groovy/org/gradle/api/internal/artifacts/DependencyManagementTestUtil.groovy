/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts

import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

class DependencyManagementTestUtil {
    private static final ComponentIdGenerator ID_GENERATOR = new ComponentIdGenerator()
    private static final AttributeDesugaring ATTRIBUTE_DESUGARING = new AttributeDesugaring(AttributeTestUtil.attributesFactory())

    static ModuleComponentGraphResolveStateFactory modelGraphResolveFactory() {
        return new ModuleComponentGraphResolveStateFactory(ID_GENERATOR, ATTRIBUTE_DESUGARING)
    }

    static MavenMutableModuleMetadataFactory mavenMetadataFactory() {
        return new MavenMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), AttributeTestUtil.attributesFactory(), TestUtil.objectInstantiator(), defaultSchema())
    }

    static IvyMutableModuleMetadataFactory ivyMetadataFactory() {
        return new IvyMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), AttributeTestUtil.attributesFactory(), defaultSchema())
    }

    static PreferJavaRuntimeVariant defaultSchema() {
        return new PreferJavaRuntimeVariant(TestUtil.objectInstantiator())
    }

    static PlatformSupport platformSupport() {
        return new PlatformSupport(TestUtil.objectInstantiator())
    }

    static ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory() {
        return new TestComponentDescriptorFactory()
    }
}
