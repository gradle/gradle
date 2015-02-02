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

class MavenPublicationModule implements IntegrityChecker {
    String[] knownArtifactTypes = ['jar', 'ear', 'war']
    String version
    MavenPom mavenPom
    MavenItem pom
    MavenItem jar
    MavenItem ear
    MavenItem war
    MavenItem source
    MavenItem javadoc
    TestFile dir

    MavenPublicationModule(TestFile dir) {
        this.dir = dir
    }

    void assertJavaModule() {
        pom.verify()
        jar.verify()
    }

    void assertSources() {
        source.verify()
    }

    void assertJavaDoc() {
        javadoc.verify()
    }

    @Override
    void check(ModuleDescriptor moduleDescriptor) {
        assert mavenPom.artifactId == moduleDescriptor.moduleName
        assert mavenPom.groupId == moduleDescriptor.organisation
        assert mavenPom.version == moduleDescriptor.revision
    }
}
