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
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import spock.lang.Specification

class DefaultLocalComponentMetaDataTest extends Specification {
    def moduleDescriptor = new DefaultModuleDescriptor(IvyUtil.createModuleRevisionId("group", "module", "version"), "status", null)
    def componentIdentifier = Mock(ComponentIdentifier)
    def metaData = new DefaultLocalComponentMetaData(moduleDescriptor, componentIdentifier)

    def "can lookup configuration after it has been added"() {
        when:
        metaData.addConfiguration("conf", true, "description", ["super"] as String[], true)

        then:
        metaData.moduleDescriptor.configurations.length == 1
        metaData.moduleDescriptor.getConfiguration("conf") != null
    }

    def "can lookup artifact in various ways after it has been added"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        moduleDescriptor.addConfiguration(new Configuration("conf"))

        when:
        metaData.addArtifact("conf", artifact, file)

        then:
        metaData.artifacts.size() == 1
        def publishArtifact = (metaData.artifacts as List).first()
        publishArtifact.id
        publishArtifact.name.name == artifact.name
        publishArtifact.name.type == artifact.type
        publishArtifact.name.extension == artifact.extension
        publishArtifact.file == file

        and:
        metaData.getArtifact(publishArtifact.id) == publishArtifact

        and:
        def resolveMetaData = metaData.toResolveMetaData()
        resolveMetaData.artifacts.size() == 1
        def resolveArtifact = (resolveMetaData.artifacts as List).first()
        resolveArtifact.id
        resolveArtifact.componentId == resolveMetaData.componentId
        resolveArtifact.name.name == artifact.name
        resolveArtifact.name.type == artifact.type
        resolveArtifact.name.extension == artifact.extension

        and:
        moduleDescriptor.getArtifacts("conf").size() == 1
        def ivyArtifact = (moduleDescriptor.getArtifacts("conf") as List).first()
        ivyArtifact.name == artifact.name
        ivyArtifact.type == artifact.type
        ivyArtifact.ext == artifact.extension
        ivyArtifact.configurations == ["conf"]
    }

    def "can add artifact to several configurations"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        moduleDescriptor.addConfiguration(new Configuration("conf1"))
        moduleDescriptor.addConfiguration(new Configuration("conf2"))
        moduleDescriptor.addConfiguration(new Configuration("conf3"))

        when:
        metaData.addArtifact("conf1", artifact, file)
        metaData.addArtifact("conf2", artifact, file)

        then:
        metaData.artifacts.size() == 1

        and:
        def resolveMetaData = metaData.toResolveMetaData()
        resolveMetaData.artifacts.size() == 1

        and:
        moduleDescriptor.getArtifacts("conf1").size() == 1
        moduleDescriptor.getArtifacts("conf2").size() == 1
        def ivyArtifact = (moduleDescriptor.getArtifacts("conf1") as List).first()
        ivyArtifact.configurations == ["conf1", "conf2"]
    }

    def "can lookup an artifact given an Ivy artifact"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        moduleDescriptor.addConfiguration(new Configuration("conf"))

        and:
        metaData.addArtifact("conf", artifact, file)

        and:
        def ivyArtifact = metaData.toResolveMetaData().descriptor.allArtifacts.find { it.name == artifact.name }

        expect:
        def resolveArtifact = metaData.toResolveMetaData().artifact(ivyArtifact)
        resolveArtifact.file == file
        resolveArtifact == metaData.getArtifact(resolveArtifact.id)
    }

    def "can lookup an unknown artifact given an Ivy artifact"() {
        def artifact = artifact()

        expect:
        def resolveArtifact = metaData.toResolveMetaData().artifact(artifact)
        resolveArtifact != null
        resolveArtifact.file == null
        metaData.getArtifact(resolveArtifact.id) == null
    }

    def "treats as distinct two artifacts with duplicate attributes and different files"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
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

        def conf2Artifacts = resolveMetaData.getConfiguration("conf2").artifacts as List
        conf2Artifacts.size() == 1
        def artifactMetadata2 = conf2Artifacts[0]

        and:
        artifactMetadata1.id != artifactMetadata2.id

        and:
        resolveMetaData.artifacts == [artifactMetadata1, artifactMetadata2] as Set

        and:
        metaData.getArtifact(artifactMetadata1.id).file == file1
        metaData.getArtifact(artifactMetadata2.id).file == file2
    }

    def "can convert to publish meta-data"() {
        def artifact = artifactName()
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
        publishArtifact.artifact.name == artifact.name
        publishArtifact.artifact.type == artifact.type
        publishArtifact.artifact.ext == artifact.extension
        publishArtifact.file == file
    }

    def artifact() {
        return new DefaultArtifact(moduleDescriptor.getModuleRevisionId(), null, "artifact", "type", "ext")
    }

    def artifactName() {
        return new DefaultIvyArtifactName("artifact", "type", "ext")
    }
}
