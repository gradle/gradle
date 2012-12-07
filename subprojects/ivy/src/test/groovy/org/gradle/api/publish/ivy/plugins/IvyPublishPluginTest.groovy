/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy.plugins

import org.gradle.api.Project
import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.IvyNormalizedPublication
import org.gradle.api.publish.ivy.internal.IvyPublicationInternal
import org.gradle.util.HelperUtil
import spock.lang.Specification

class IvyPublishPluginTest extends Specification {

    Project project = HelperUtil.createRootProject()
    PublishingExtension extension

    def setup() {
        project.plugins.apply(IvyPublishPlugin)
        extension = project.extensions.getByType(PublishingExtension)
    }

    def "installs ivy publication"() {
        expect:
        extension.publications.size() == 1
        extension.publications.toList().first() instanceof IvyPublication

        IvyPublicationInternal publication = extension.publications.ivy
        publication.name == "ivy"

        when:
        project.group = "foo"
        project.version = 1.0
        project.status = "integration"

        and:
        IvyNormalizedPublication normalizedPublication = publication.asNormalisedPublication()

        then:
        normalizedPublication.module.name == project.name
        normalizedPublication.module.group == project.group
        normalizedPublication.module.version == project.version.toString()
        normalizedPublication.module.status == project.status

        when:
        project.group = "bar"
        project.version = 2.0
        project.status = "final"
        normalizedPublication = publication.asNormalisedPublication()

        then:
        normalizedPublication.module.group == project.group
        normalizedPublication.module.version == project.version.toString()
        normalizedPublication.module.status == project.status
    }

    def "can configure descriptor"() {
        given:
        IvyPublicationInternal publication = extension.publications.ivy

        when:
        publication.descriptor {
            withXml {
                it.asNode().@foo = "bar"
            }
        }

        then:
        def transformer = new XmlTransformer()
        transformer.addAction(publication.descriptor.xmlAction)
        transformer.transform("<things/>").contains('things foo="bar"')
    }
}
