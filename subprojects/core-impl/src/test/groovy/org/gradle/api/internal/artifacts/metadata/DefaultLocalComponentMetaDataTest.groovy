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

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import spock.lang.Specification

class DefaultLocalComponentMetaDataTest extends Specification {
    def metaData = new DefaultLocalComponentMetaData(Stub(DefaultModuleDescriptor))

    def "can add artifacts"() {
        def artifact = Stub(Artifact)
        def file = new File("artifact.zip")

        when:
        metaData.addArtifact(artifact, file)

        then:
        metaData.artifacts.size() == 1
        def artifacts = metaData.artifacts as List
        def publishArtifact = artifacts[0]
        publishArtifact.id
        publishArtifact.file == file

        and:
        metaData.getArtifact(publishArtifact.id) == publishArtifact
    }

    def "can convert to publish meta-data"() {
        def artifact = Stub(Artifact)
        def file = new File("artifact.zip")

        given:
        metaData.addArtifact(artifact, file)

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
}
