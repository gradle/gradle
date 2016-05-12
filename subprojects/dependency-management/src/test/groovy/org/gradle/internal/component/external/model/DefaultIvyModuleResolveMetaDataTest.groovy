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

package org.gradle.internal.component.external.model

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ExcludeRule
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import org.gradle.internal.component.model.DependencyMetaData
import spock.lang.Ignore

// TODO:DAZ Re-enable this when the de-ivy-fication is complete
@Ignore
class DefaultIvyModuleResolveMetaDataTest extends AbstractModuleComponentResolveMetaDataTest {

    @Override
    AbstractModuleComponentResolveMetaData createMetaData(ModuleVersionIdentifier id, ModuleDescriptor moduleDescriptor, ModuleComponentIdentifier componentIdentifier) {
        moduleDescriptor.getModuleRevisionId() >> IvyUtil.createModuleRevisionId(id)
        moduleDescriptor.getConfigurationsNames() >> new String[0]
        moduleDescriptor.getAllArtifacts() >> new Artifact[0]
        moduleDescriptor.getDependencies() >> new DependencyDescriptor[0]
        moduleDescriptor.getAllExcludeRules() >> new ExcludeRule[0]
        return new DefaultIvyModuleResolveMetaData(componentIdentifier, moduleDescriptor)
    }

    def "can make a copy"() {
        def dependency1 = Stub(DependencyMetaData)
        def dependency2 = Stub(DependencyMetaData)

        given:
        metaData.changing = true
        metaData.dependencies = [dependency1, dependency2]
        metaData.status = 'a'
        metaData.statusScheme = ['a', 'b', 'c']

        when:
        def copy = metaData.copy()

        then:
        copy != metaData
        copy.descriptor == moduleDescriptor
        copy.changing
        copy.dependencies == [dependency1, dependency2]
        copy.status == 'a'
        copy.statusScheme == ['a', 'b', 'c']
    }

    def "getBranch returns branch from moduleDescriptor" () {
        setup:
        def moduleRevisionId = ModuleRevisionId.newInstance('orgId', 'moduleId', expectedBranch, 'version')
        def descriptor = Stub(ModuleDescriptor) {
            getModuleRevisionId() >> moduleRevisionId
        }
        def metaDataWithBranch = new DefaultIvyModuleResolveMetaData(componentId, descriptor)

        expect:
        metaDataWithBranch.branch == expectedBranch

        where:
        expectedBranch | _
        null           | _
        'someBranch'   | _
    }
}
