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

package org.gradle.test.fixtures.maven.publication

import org.gradle.test.fixtures.maven.IntegrityChecker
import org.gradle.test.fixtures.maven.ModuleDescriptor

class SnapshotMetaData implements IntegrityChecker {

    String groupId
    String artifactId
    String version
    String lastUpdated
    String timestamp
    int buildNumber
    MavenItem snapshotMetaDataItem

    SnapshotMetaData(MavenItem snapshotMetaDataItem) {
        this.snapshotMetaDataItem = snapshotMetaDataItem
        def xml = new XmlParser().parseText(snapshotMetaDataItem.file.text)
        groupId = xml.groupId[0]?.text()
        artifactId = xml.artifactId[0]?.text()
        version = xml.version[0]?.text()
        def versioning = xml.versioning[0]
        lastUpdated = versioning.lastUpdated[0]?.text()
        timestamp = versioning.snapshot[0].timestamp.text()
        buildNumber = versioning.snapshot[0].buildNumber.text().toInteger()
    }

    @Override
    void check(ModuleDescriptor moduleDescriptor) {
        snapshotMetaDataItem.verify()
        assert version == moduleDescriptor.revision
        assert artifactId == moduleDescriptor.moduleName
        assert groupId == moduleDescriptor.organisation
        assert buildNumber > 0
        assert timestamp
        assert lastUpdated ==~ /\d{14}/
        assert timestamp ==~ /\d{8}\.\d{6}/
    }
}
