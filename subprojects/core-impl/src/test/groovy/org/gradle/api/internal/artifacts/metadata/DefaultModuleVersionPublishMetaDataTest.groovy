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
import org.gradle.api.artifacts.ModuleVersionIdentifier
import spock.lang.Specification

class DefaultModuleVersionPublishMetaDataTest extends Specification {
    def metaData = new DefaultModuleVersionPublishMetaData(Stub(ModuleVersionIdentifier))

    def "can add artifacts"() {
        def artifact = Stub(Artifact)
        def file = new File("artifact.zip")

        when:
        metaData.addArtifact(artifact, file)

        then:
        metaData.artifacts.size() == 1
        def publishArtifact = metaData.artifacts.iterator().next()
        publishArtifact.artifact == artifact
        publishArtifact.file == file

        and:
        metaData.getArtifact(publishArtifact.id) == publishArtifact
    }
}
