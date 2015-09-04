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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.internal.component.local.model.BuildableLocalComponentMetaData
import spock.lang.Specification

class DefaultConfigurationsToArtifactsConverterTest extends Specification {
    def converter = new DefaultConfigurationsToArtifactsConverter()

    def "adds artifacts from each configuration"() {
        def metaData = Mock(BuildableLocalComponentMetaData)
        def config1 = Stub(Configuration)
        def config2 = Stub(Configuration)
        def artifacts1 = Stub(PublishArtifactSet)
        def artifacts2 = Stub(PublishArtifactSet)

        given:
        config1.name >> "config1"
        config1.artifacts >> artifacts1
        config2.name >> "config2"
        config2.artifacts >> artifacts2


        when:
        converter.addArtifacts(metaData, [config1, config2])

        then:
        1 * metaData.addArtifacts("config1", artifacts1)
        1 * metaData.addArtifacts("config2", artifacts2)
        0 * metaData._
    }
}
