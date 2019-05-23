/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile

/**
 * http://maven.apache.org/ref/3.0.1/maven-repository-metadata/repository-metadata.html
 */
class DefaultSnapshotMavenMetaData implements SnapshotMavenMetaData {

    String text
    String groupId
    String artifactId
    String version

    boolean localSnapshot
    String snapshotTimestamp
    String snapshotBuildNumber
    List<String> snapshotVersions = []
    String lastUpdated

    final String path
    final TestFile file

    DefaultSnapshotMavenMetaData(String path, TestFile file) {
        this.file = file
        this.path = path
        if (!file.exists()) {
            return
        }

        text = file.text
        def xml = new XmlParser().parseText(text)

        groupId = xml.groupId[0]?.text()
        artifactId = xml.artifactId[0]?.text()
        version = xml.version[0]?.text()

        def versioning = xml.versioning[0]

        lastUpdated = versioning.lastUpdated[0]?.text()
        snapshotTimestamp =  versioning.snapshot.timestamp[0]?.text()
        snapshotBuildNumber =  versioning.snapshot.buildNumber[0]?.text()
        localSnapshot = versioning.snapshot.localCopy[0]?.text() == 'true'

        def snapshotVersionCollector = new LinkedHashSet<String>()
        versioning.snapshotVersions.snapshotVersion.value.each {
            snapshotVersionCollector << it.text()
        }
        snapshotVersions = snapshotVersionCollector as List<String>
    }
}
