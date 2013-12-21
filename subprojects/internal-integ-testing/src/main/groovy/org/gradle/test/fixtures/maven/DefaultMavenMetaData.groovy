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

/**
 * http://maven.apache.org/ref/3.0.1/maven-repository-metadata/repository-metadata.html
 */
class DefaultMavenMetaData implements MavenMetaData{

    String text

    String groupId
    String artifactId
    String version

    List<String> versions = []
    String lastUpdated

    DefaultMavenMetaData(File file) {
        text = file.text
        def xml = new XmlParser().parseText(text)

        groupId = xml.groupId[0]?.text()
        artifactId = xml.artifactId[0]?.text()
        version = xml.version[0]?.text()

        def versioning = xml.versioning[0]

        lastUpdated = versioning.lastUpdated[0]?.text()

        versioning.versions[0].version.each {
            versions << it.text()
        }
    }



}
