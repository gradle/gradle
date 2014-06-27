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

package org.gradle.api.internal.artifacts.metadata

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.MDArtifact
import org.apache.ivy.core.module.id.ModuleRevisionId
import spock.lang.Specification

class BuildableIvyModuleVersionMetaDataTest extends Specification {
    def descriptor = new DefaultModuleDescriptor(ModuleRevisionId.newInstance("org", "module", "rev"), "broken", null)
    def metaData = new BuildableIvyModuleVersionMetaData(descriptor)

    def "can attach artifacts to meta-data"() {
        def artifact = new MDArtifact(descriptor, "thing", "type", "ext")
        artifact.addConfiguration("conf")
        descriptor.addConfiguration(new Configuration("conf"))

        when:
        metaData.addArtifact(artifact)

        then:
        metaData.artifacts.size() == 1
        metaData.getConfiguration("conf").artifacts.size() == 1
        metaData.descriptor.allArtifacts == [artifact]
        metaData.descriptor.getArtifacts("conf") == [artifact]
    }
}
