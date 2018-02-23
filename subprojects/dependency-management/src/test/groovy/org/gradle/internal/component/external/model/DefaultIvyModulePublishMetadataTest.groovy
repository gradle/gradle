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

package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.ivypublish.DefaultIvyModulePublishMetadata
import org.gradle.internal.component.local.model.LocalConfigurationMetadata
import org.gradle.internal.component.model.IvyArtifactName
import spock.lang.Specification

class DefaultIvyModulePublishMetadataTest extends Specification {
    def metadata = new DefaultIvyModulePublishMetadata(Stub(ModuleComponentIdentifier), "status")

    def "can add artifacts"() {
        def artifact = Stub(IvyArtifactName)
        def file = new File("artifact.zip")

        when:
        metadata.addArtifact(artifact, file)

        then:
        metadata.artifacts.size() == 1
        def publishArtifact = metadata.artifacts.iterator().next()
        publishArtifact.artifactName == artifact
        publishArtifact.file == file
    }

    def "can add configuration"() {
        when:
        metadata.addConfiguration("configName", ["one", "two", "three"] as Set, true, true)

        then:
        metadata.configurations.size() == 1
        Configuration conf = metadata.configurations["configName"]
        conf.name == "configName"
        conf.extendsFrom == ["one", "three", "two"]
        conf.visible
        conf.transitive
    }

    def mockConfiguration() {
        return Stub(LocalConfigurationMetadata) { configuration ->
            configuration.name >> "configName"
            configuration.description >> "configDescription"
            configuration.extendsFrom >> (["one", "two", "three"] as Set)
            configuration.visible >> true
            configuration.transitive >> true
        }
    }
}
