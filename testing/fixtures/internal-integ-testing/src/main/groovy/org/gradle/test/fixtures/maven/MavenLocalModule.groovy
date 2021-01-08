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

import org.gradle.test.fixtures.file.TestFile

class MavenLocalModule extends MavenFileModule {
    private boolean uniqueSnapshots = false;

    MavenLocalModule(TestFile rootDir, TestFile moduleDir, String groupId, String artifactId, String version) {
        super(rootDir, moduleDir, groupId, artifactId, version)
    }

    @Override
    boolean getUniqueSnapshots() {
        return uniqueSnapshots
    }

    @Override
    MavenLocalModule withNonUniqueSnapshots() {
        //NO-OP for mavenLocal cache.
        this
    }

    @Override
    protected String getMetadataFileName() {
        return "maven-metadata-local.xml"
    }

    @Override
    String getMetaDataFileContent() {
        """
<metadata>
  <!-- ${getArtifactContent()} -->
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <version>$version</version>
  <versioning>
    <snapshot>
      <localCopy>true</localCopy>
    </snapshot>
    <lastUpdated>${updateFormat.format(publishTimestamp)}</lastUpdated>
  </versioning>
</metadata>
"""
    }

    @Override
    protected onPublish(TestFile file) {
    }

    @Override
    protected boolean publishesMetaDataFile() {
        version.endsWith("-SNAPSHOT")
    }

    /* No checksums published for local modules */
    @Override
    void assertArtifactsPublished(String... names) {
        assert moduleDir.list() as Set == names as Set
    }
}
