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

package org.gradle.internal.component.local.model
import org.apache.ivy.core.module.descriptor.Configuration
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetaData
import spock.lang.Specification

class DefaultLocalComponentMetaDataTest extends Specification {
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "version")
    def componentIdentifier = DefaultModuleComponentIdentifier.newId(id)
    def metaData = new DefaultLocalComponentMetaData(id, componentIdentifier, "status")

    def "can lookup configuration after it has been added"() {
        when:
        metaData.addConfiguration("super", false, "description", [] as String[], false)
        metaData.addConfiguration("conf", true, "description", ["super"] as String[], true)

        then:
        def resolveMetaData = metaData.toResolveMetaData()
        resolveMetaData.configurationNames == ['conf', 'super'] as Set

        def conf = resolveMetaData.getConfiguration('conf')
        conf != null
        conf.public
        conf.transitive

        def superConf = resolveMetaData.getConfiguration('super')
        superConf != null
        !superConf.public
        !superConf.transitive

        and:
        def publishMetaData = metaData.toPublishMetaData()
        publishMetaData.getModuleDescriptor().configurations.length == 2
        publishMetaData.getModuleDescriptor().getConfiguration('conf') != null

        def ivyConf = publishMetaData.getModuleDescriptor().getConfiguration('conf')
        ivyConf != null
        ivyConf.transitive
        ivyConf.visibility == Configuration.Visibility.PUBLIC
    }

    def "can lookup artifact in various ways after it has been added"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        metaData.addConfiguration("conf", true, "", [] as String[], true)

        when:
        metaData.addArtifact("conf", artifact, file)

        then:
        def resolveMetaData = metaData.toResolveMetaData()
        resolveMetaData.artifacts.size() == 1

        def publishArtifact = resolveMetaData.artifact(artifact)
        publishArtifact.id
        publishArtifact.name.name == artifact.name
        publishArtifact.name.type == artifact.type
        publishArtifact.name.extension == artifact.extension
        publishArtifact.file == file

        and:
        def publishMetaData = metaData.toPublishMetaData()
        publishMetaData.artifacts.size() == 1
        def publishMetaDataArtifact = (publishMetaData.artifacts as List).first()
        publishMetaDataArtifact.id
        publishMetaDataArtifact.id.componentIdentifier == componentIdentifier
        publishMetaDataArtifact.artifactName.name == artifact.name
        publishMetaDataArtifact.artifactName.type == artifact.type
        publishMetaDataArtifact.artifactName.extension == artifact.extension
    }

    def "can add artifact to several configurations"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        metaData.addConfiguration("conf1", true, "", [] as String[], true)
        metaData.addConfiguration("conf2", true, "", [] as String[], true)

        when:
        metaData.addArtifact("conf1", artifact, file)
        metaData.addArtifact("conf2", artifact, file)

        then:
        def resolveMetaData = metaData.toResolveMetaData()
        resolveMetaData.artifacts.size() == 1
    }

    def "can lookup an artifact given an Ivy artifact"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        metaData.addConfiguration("conf", true, "", [] as String[], true)

        and:
        metaData.addArtifact("conf", artifact, file)

        and:
        def ivyArtifact = artifactName()

        expect:
        def resolveArtifact = metaData.toResolveMetaData().artifact(ivyArtifact)
        resolveArtifact.file == file
    }

    def "can lookup an unknown artifact given an Ivy artifact"() {
        def artifact = artifactName()

        expect:
        def resolveArtifact = metaData.toResolveMetaData().artifact(artifact)
        resolveArtifact != null
        resolveArtifact.file == null
    }

    def "treats as distinct two artifacts with duplicate attributes and different files"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")

        given:
        metaData.addConfiguration("conf1", true, "conf1", new String[0], true)
        metaData.addConfiguration("conf2", true, "conf2", new String[0], true)
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
    }

    def "can add dependencies"() {
        def dependency = Mock(DependencyMetaData)

        when:
        metaData.addDependency(dependency)

        then:
        metaData.toResolveMetaData().dependencies == [dependency]

        // TODO:DAZ Test conversion of dependency meta data for publishing
//        and:
//        def ivyDependencies = metaData.toPublishMetaData().getModuleDescriptor().dependencies
//        ivyDependencies.length == 1
    }

    def artifactName() {
        return new DefaultIvyArtifactName("artifact", "type", "ext")
    }
}
