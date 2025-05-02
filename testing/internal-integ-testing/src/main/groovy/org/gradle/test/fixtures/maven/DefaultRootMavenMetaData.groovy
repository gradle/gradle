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

package org.gradle.test.fixtures.maven

import groovy.xml.XmlParser
import org.gradle.test.fixtures.file.TestFile

/**
 * http://maven.apache.org/ref/3.0.1/maven-repository-metadata/repository-metadata.html
 */
class DefaultRootMavenMetaData implements RootMavenMetaData {

    String text

    String groupId
    String artifactId

    String releaseVersion
    String latestVersion

    List<String> versions = []
    String lastUpdated
    final String path
    final TestFile file

    DefaultRootMavenMetaData(String path, TestFile file) {
        this.file = file
        this.path = path
        if (!file.exists()) {
            return
        }

        text = file.text
        def xml = new XmlParser().parseText(text)

        groupId = xml.groupId[0]?.text()
        artifactId = xml.artifactId[0]?.text()

        def versioning = xml.versioning[0]

        lastUpdated = versioning.lastUpdated[0]?.text()
        releaseVersion = versioning.release[0]?.text()
        latestVersion = versioning.latest[0]?.text()

        versioning.versions[0].version.each {
            versions << it.text()
        }
    }

    String getName() {
        return file.name
    }
}
