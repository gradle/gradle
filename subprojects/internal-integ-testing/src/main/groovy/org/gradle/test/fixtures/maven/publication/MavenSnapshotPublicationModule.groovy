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
import org.gradle.test.fixtures.maven.MavenPom
import org.gradle.test.fixtures.maven.ModuleDescriptor


class MavenSnapshotPublicationModule extends MavenPublicationModule implements IntegrityChecker {
    private String moduleName
    private String snapshotVersion

    MavenSnapshotPublicationModule(String moduleName, String snapshotVersion, TestFile dir, List<String> artifacts) {
        super(dir)
        this.snapshotVersion = snapshotVersion
        this.moduleName = moduleName
        this.dir = dir
        this.version = "$moduleName-${dir.name}-$snapshotVersion"
        createMavenItems(dir, artifacts)
        this.mavenPom = new MavenPom(pom.file)
    }


    void createMavenItems(TestFile dir, List<String> artifacts) {
        (knownArtifactTypes + 'pom').each { String fileType ->
            artifacts.each { String fileName ->
                def expression = /($moduleName.+$snapshotVersion)\.$fileType$/
                def matcher = (fileName =~ expression)
                if (matcher) {
                    this."$fileType" = new MavenItem(dir, fileName)
                    String baseName = matcher[0][1]
                    source = new MavenItem(dir, "$baseName-sources.jar")
                    javadoc = new MavenItem(dir, "$baseName-javadoc.jar")
                }
            }

        }
    }


    @Override
    void check(ModuleDescriptor moduleDescriptor) {
        super.check(moduleDescriptor)
    }
}