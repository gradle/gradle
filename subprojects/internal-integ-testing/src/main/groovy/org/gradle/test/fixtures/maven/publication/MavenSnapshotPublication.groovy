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

import groovy.io.FileType
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.ModuleDescriptor

class MavenSnapshotPublication extends MavenPublication {
    MavenSnapshotPublication(TestFile baseDir, ModuleDescriptor moduleDescriptor) {
        super(baseDir, moduleDescriptor)
    }

    List<MavenReleasePublicationModule> readVersions() {
        def all = []
        moduleDir.eachFile FileType.DIRECTORIES, {
            all << new MavenSnapshotModule(it, moduleDescriptor.moduleName)
        }
        all
    }

    @Override
    boolean assertJavaPublication() {
        check(moduleDescriptor)
        MavenSnapshotModule mavenSnapshotModule = getVersion(moduleDescriptor.revision)
        assert mavenSnapshotModule.versions
        mavenSnapshotModule.versions.each { MavenSnapshotPublicationModule snapshotPublicationModule ->
            snapshotPublicationModule.check(moduleDescriptor)
            snapshotPublicationModule.assertJavaModule()
        }
        true
    }

    @Override
    boolean assertJavaPublicationWithSourceAndJavadoc() {
        check(moduleDescriptor)
        MavenSnapshotModule mavenSnapshotModule = getVersion(moduleDescriptor.revision)
        assert mavenSnapshotModule.versions
        mavenSnapshotModule.versions.each { MavenSnapshotPublicationModule snapshotPublicationModule ->
            snapshotPublicationModule.check(moduleDescriptor)
            snapshotPublicationModule.assertJavaModule()
            snapshotPublicationModule.assertSources()
            snapshotPublicationModule.assertJavaDoc()
        }
        true
    }

    @Override
    void check(ModuleDescriptor moduleDescriptor) {
        rootMetadata.check(moduleDescriptor)
        assert rootMetadata.mavenMetaData.versions.size() == versions.size()
    }

    boolean assertLatestBuild(int i) {
        MavenSnapshotModule module = getVersion(moduleDescriptor.revision)
        module.check(moduleDescriptor)
        assert module.versions.size() == i
        assert module.snapshotMetaData.buildNumber == i
        MavenSnapshotPublicationModule spm = module.versions[i -1]
        assert spm.snapshotVersion == "${module.snapshotMetaData.timestamp}-$i"
        true
    }
}
