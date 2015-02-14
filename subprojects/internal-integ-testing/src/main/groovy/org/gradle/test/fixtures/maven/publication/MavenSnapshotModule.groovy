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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.IntegrityChecker
import org.gradle.test.fixtures.maven.ModuleDescriptor

import static org.gradle.test.fixtures.maven.publication.MavenPublication.MAVEN_METADATA_FILE

class MavenSnapshotModule implements PublishedModule, IntegrityChecker {
    List<MavenSnapshotPublicationModule> versions = []
    MavenItem snapshotMetaDataItem
    SnapshotMetaData snapshotMetaData
    String version
    private String moduleName

    MavenSnapshotModule(TestFile dir, String moduleName) {
        readMetaData(dir)
        readVersions(moduleName, dir)
        this.version = dir.name

    }

    private void readVersions(String moduleName, TestFile dir) {
        this.moduleName = moduleName
        def all = dir.listFiles().sort { File f -> f.lastModified() }.collect { File f -> f.name }
        Map allVersions = [:]
        all.each { String fileName ->
            def matcher = (fileName =~ /.*-(\d{8}\.\d{6}-\d+)\.+/)

            if (matcher) {

                String version = matcher[0][1]
                if (allVersions[version]) {
                    allVersions[version] << fileName
                } else {
                    allVersions[version] = [fileName]
                }

            }
        }
        allVersions.each { String snapshotVersion, List artifacts ->
            versions << new MavenSnapshotPublicationModule(moduleName, snapshotVersion, dir, artifacts)
        }
    }

    private void readMetaData(TestFile dir) {
        this.snapshotMetaDataItem = new MavenItem(dir, "${MAVEN_METADATA_FILE}")
        this.snapshotMetaData = new SnapshotMetaData(snapshotMetaDataItem)
    }

    @Override
    String getVersion() {
        return version
    }

    @Override
    void check(ModuleDescriptor moduleDescriptor) {
        snapshotMetaData.check(moduleDescriptor)
    }
}
