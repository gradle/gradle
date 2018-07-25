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

import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.file.TestFile

class MavenFileModule extends AbstractMavenModule {
    private boolean uniqueSnapshots = true;

    MavenFileModule(TestFile rootDir, TestFile moduleDir, String groupId, String artifactId, String version) {
        super(rootDir, moduleDir, groupId, artifactId, version)
    }

    @Override
    MavenFileModule dependsOn(Module module) {
        super.dependsOn(module)
        return this
    }

    @Override
    MavenFileModule dependsOn(Map<String, ?> attributes, Module module) {
        super.dependsOn(attributes, module)
        return this
    }

    @Override
    MavenFileModule artifact(Map<String, ?> options) {
        super.artifact(options)
        return this
    }

    @Override
    boolean getUniqueSnapshots() {
        return uniqueSnapshots
    }

    @Override
    MavenFileModule withNonUniqueSnapshots() {
        uniqueSnapshots = false;
        return this;
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
      <timestamp>${timestampFormat.format(publishTimestamp)}</timestamp>
      <buildNumber>$publishCount</buildNumber>
    </snapshot>
    <lastUpdated>${updateFormat.format(publishTimestamp)}</lastUpdated>
  </versioning>
</metadata>
"""
    }

    @Override
    protected onPublish(TestFile file) {
        sha1File(file)
        md5File(file)
    }

    @Override
    protected boolean publishesMetaDataFile() {
        uniqueSnapshots && version.endsWith("-SNAPSHOT")
    }

}
