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
import org.gradle.test.fixtures.maven.IntegrityChecker
import org.gradle.test.fixtures.maven.ModuleDescriptor

class MavenReleasePublication extends MavenPublication implements IntegrityChecker {

    MavenReleasePublication(TestFile baseDir, ModuleDescriptor moduleDescriptor) {
        super(baseDir, moduleDescriptor)
    }

    List<MavenReleasePublicationModule> readVersions() {
        def all = []
        moduleDir.eachFile FileType.DIRECTORIES, {
            all << new MavenReleaseModule(it, moduleDescriptor.moduleName)
        }
        all
    }

    @Override
    boolean assertJavaPublicationWithSourceAndJavadoc() {
        check(moduleDescriptor)
        MavenReleaseModule mavenPublishModule = getVersion(moduleDescriptor.revision)
        mavenPublishModule.module.assertJavaModule()
        mavenPublishModule.module.assertSources()
        mavenPublishModule.module.assertJavaDoc()
        mavenPublishModule.module.check(moduleDescriptor)
        true
    }

    @Override
    boolean assertJavaPublication() {
        check(moduleDescriptor)
        MavenReleaseModule mavenPublishModule = getVersion(moduleDescriptor.revision)
        mavenPublishModule.module.assertJavaModule()
        mavenPublishModule.module.check(moduleDescriptor)
        true
    }

    @Override
    void check(ModuleDescriptor moduleDescriptor) {
        assert rootMetadata.mavenMetaData.versions.size() == versions.size()
        rootMetadata.check(moduleDescriptor)
    }
}
