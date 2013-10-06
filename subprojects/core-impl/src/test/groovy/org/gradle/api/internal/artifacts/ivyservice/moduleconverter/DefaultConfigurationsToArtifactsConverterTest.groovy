/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.artifacts.metadata.MutableLocalComponentMetaData
import spock.lang.Specification

class DefaultConfigurationsToArtifactsConverterTest extends Specification {
    def converter = new DefaultConfigurationsToArtifactsConverter()

    def "adds artifacts from each configuration"() {
        def metaData = Mock(MutableLocalComponentMetaData)
        def config1 = Stub(Configuration)
        def config2 = Stub(Configuration)
        def artifact1 = Stub(PublishArtifact)
        def artifact2 = Stub(PublishArtifact)
        def file1 = new File("file-1")
        def file2 = new File("file-2")

        given:
        config1.name >> "config1"
        config1.artifacts >> new DefaultPublishArtifactSet("art1", new DefaultDomainObjectSet<PublishArtifact>(PublishArtifact, [artifact1]))
        config2.name >> "config2"
        config2.artifacts >> new DefaultPublishArtifactSet("art1", new DefaultDomainObjectSet<PublishArtifact>(PublishArtifact, [artifact2]))

        and:
        artifact1.name >> 'art1'
        artifact1.type >> 'type1'
        artifact1.extension >> 'ext1'
        artifact2.name >> 'art2'
        artifact2.type >> 'type2'
        artifact2.extension >> 'ext2'
        artifact2.classifier >> 'classifier'

        when:
        converter.addArtifacts(metaData, [config1, config2])

        then:
        1 * metaData.addArtifact("config1", _, file1) >> { name, Artifact artifact, file ->
            assert artifact.name == 'art1'
            assert artifact.type == 'type1'
            assert artifact.ext == 'ext1'
            assert artifact.qualifiedExtraAttributes == [:]
        }
        1 * metaData.addArtifact("config2", _, file2) >> { name, Artifact artifact, file ->
            assert artifact.name == 'art2'
            assert artifact.type == 'type2'
            assert artifact.ext == 'ext2'
            assert artifact.qualifiedExtraAttributes == [(Dependency.CLASSIFIER): 'classifier']
        }
        _ * metaData.moduleDescriptor >> Stub(DefaultModuleDescriptor)
        0 * metaData._
    }

    def "artifact name defaults to module name when not specified"() {
        def metaData = Mock(MutableLocalComponentMetaData)
        def config = Stub(Configuration)
        def artifact = Stub(PublishArtifact)
        def file = new File("file-1")

        given:
        config.name >> "config1"
        config.artifacts >> new DefaultPublishArtifactSet("art1", new DefaultDomainObjectSet<PublishArtifact>(PublishArtifact, [artifact]))

        and:
        artifact.type >> 'type1'
        artifact.extension >> 'ext1'

        when:
        converter.addArtifacts(metaData, [config])

        then:
        1 * metaData.addArtifact("config1", _, file) >> { name, Artifact ivyArtifact, f ->
            assert ivyArtifact.name == 'module'
        }
        _ * metaData.moduleDescriptor >> Stub(DefaultModuleDescriptor) {
            getModuleRevisionId() >> ModuleRevisionId.newInstance("group", "module", "version")
        }
        0 * metaData._
    }
}
