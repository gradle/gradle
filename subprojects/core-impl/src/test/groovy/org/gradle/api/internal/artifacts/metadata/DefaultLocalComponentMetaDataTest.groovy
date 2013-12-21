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

package org.gradle.api.internal.artifacts.metadata

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.component.ComponentIdentifier
import spock.lang.Specification

class DefaultLocalComponentMetaDataTest extends Specification {
    def moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(ModuleRevisionId.newInstance("group", "module", "version"))
    def componentIdentifier = Mock(ComponentIdentifier)
    def metaData = new DefaultLocalComponentMetaData(moduleDescriptor, componentIdentifier)

    def "can add artifacts"() {
        def artifact = artifact()
        def file = new File("artifact.zip")

        given:
        moduleDescriptor.addConfiguration(new Configuration("conf"))

        when:
        metaData.addArtifact("conf", artifact, file)

        then:
        metaData.artifacts.size() == 1
        def artifacts = metaData.artifacts as List
        def publishArtifact = artifacts[0]
        publishArtifact.id
        publishArtifact.file == file

        and:
        metaData.getArtifact(publishArtifact.id) == publishArtifact

        and:
        moduleDescriptor.getArtifacts("conf") == [artifact]
    }

    def "handles artifacts with duplicate attributes and different files"() {
        def artifact1 = artifact()
        def artifact2 = artifact()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")

        given:
        moduleDescriptor.addConfiguration(new Configuration("conf1"))
        moduleDescriptor.addConfiguration(new Configuration("conf2"))
        metaData.addArtifact("conf1", artifact1, file1)
        metaData.addArtifact("conf2", artifact2, file2)

        when:
        def resolveMetaData = metaData.toResolveMetaData()

        then:
        def conf1Artifacts = resolveMetaData.getConfiguration("conf1").artifacts as List
        conf1Artifacts.size() == 1
        def artifactMetadata1 = conf1Artifacts[0]
        artifactMetadata1.artifact.is(artifact1)

        def conf2Artifacts = resolveMetaData.getConfiguration("conf2").artifacts as List
        conf2Artifacts.size() == 1
        def artifactMetadata2 = conf2Artifacts[0]
        artifactMetadata2.artifact.is(artifact2)

        and:
        artifactMetadata1.id != artifactMetadata2.id

        and:
        metaData.getArtifact(artifactMetadata1.id).file == file1
        metaData.getArtifact(artifactMetadata2.id).file == file2
    }

    def "can convert to publish meta-data"() {
        def artifact = artifact()
        def file = new File("artifact.zip")

        given:
        moduleDescriptor.addConfiguration(new Configuration("conf"))
        metaData.addArtifact("conf", artifact, file)

        when:
        def publishMetaData = metaData.toPublishMetaData()

        then:
        publishMetaData.id == metaData.id

        and:
        publishMetaData.artifacts.size() == 1
        def artifacts = publishMetaData.artifacts as List
        def publishArtifact = artifacts[0]
        publishArtifact.artifact == artifact
        publishArtifact.file == file
    }

    def artifact() {
        return new DefaultArtifact(moduleDescriptor.getModuleRevisionId(), null, "artifact", "type", "ext")
    }
}
