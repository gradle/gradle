/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model

import org.gradle.api.InvalidUserDataException
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.util.TestUtil
import spock.lang.Specification

class EclipseProjectTest extends Specification {

    def eclipseProject = TestUtil.newInstance(EclipseProject, Mock(XmlFileContentMerger))

    def "allows adding linked resources"() {
        when:
        eclipseProject.linkedResource(name: 'foo', type: 'folder', location: '/stuff/foo')
        eclipseProject.linkedResource(name: 'bar', type: 'uri', locationUri: 'file:///stuff/bar')

        then:
        2 == eclipseProject.linkedResources.size()
    }

    def "complains when invalid link created"() {
        when:
        eclipseProject.linkedResource(name: 'foo', type: 'folder', wrongKey: '/stuff/foo')

        then:
        thrown(InvalidUserDataException.class)

        when:
        eclipseProject.linkedResource(name: 'foo', type: 'folder', location: '/stuff/foo', locationUri: 'file:///boooo')

        then:
        thrown(IllegalArgumentException.class)

        when:
        eclipseProject.linkedResource(name: 'foo', type: 'folder')

        then:
        thrown(IllegalArgumentException.class)
    }
}
