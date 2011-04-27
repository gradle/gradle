/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.build

import spock.lang.Specification
import java.text.SimpleDateFormat
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import java.text.DateFormat

class ReleasesTest extends Specification {
    Releases releases
    Project project
    File releasesXml
    final DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssZ")
    final Date buildTime = new Date()

    def setup() {
        project = ProjectBuilder.builder().build()
        releasesXml = project.file('releases.xml')
        releases = new Releases(releasesXml, project)
    }

    def "determines next release version from resources.xml"() {
        releasesXml << '''
<releases>
    <next version="1.2-preview-45"/>
    <current version="ignore-me" build-time="ignore-me"/>
    <release version="ignore-me" build-time="ignore-me"/>
</releases>
'''

        expect:
        releases.nextVersion == '1.2-preview-45'
    }

    def "generates resources.xml resource"() {
        def destFile = project.file('dest.xml')
        releasesXml << '''
<releases>
    <next version="1.2-preview-45"/>
    <current version="ignore-me" build-time="ignore-me"/>
    <release version="ignore-me" build-time="ignore-me"/>
</releases>
'''
        project.version = [versionNumber: '1.0-milestone-2', buildTime: buildTime]

        when:
        releases.generateTo(destFile)

        then:
        destFile.text == """<releases>
  <current version="1.0-milestone-2" build-time="${dateFormat.format(buildTime)}"/>
  <release version="ignore-me" build-time="ignore-me"/>
</releases>
"""
    }

    def calculatesNextVersion() {
        expect:
        releases.calculateNextVersion('1.0') == '1.1-milestone-1'
        releases.calculateNextVersion('1.1.0') == '1.1.1-milestone-1'
        releases.calculateNextVersion('1.1.2.45') == '1.1.2.46-milestone-1'
        releases.calculateNextVersion('1.0-milestone-2') == '1.0-milestone-3'
        releases.calculateNextVersion('1.0-milestone-2a') == '1.0-milestone-3'
        releases.calculateNextVersion('1.0-rc-2') == '1.0-rc-3'
    }

    def updatesReleasesXmlToIncrementNextVersion() {
        releasesXml << '''
<releases>
    <next version="1.0-milestone-2"/>
    <current version="${version}" build-time="${build=time}"/>
    <release version="previous" build-time="20101220123412-0200"/>
</releases>
'''
        project.version = [buildTime: buildTime]

        when:
        releases.incrementNextVersion()

        then:
        releasesXml.text == """<releases>
    <next version="1.0-milestone-3"/>
    <current version="\${version}" build-time="\${build=time}"/>
    <release version="1.0-milestone-2" build-time="${dateFormat.format(buildTime)}"/>
    <release version="previous" build-time="20101220123412-0200"/>
</releases>
"""
    }

}
