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

import org.gradle.test.fixtures.maven.DefaultMavenMetaData
import org.gradle.test.fixtures.maven.IntegrityChecker
import org.gradle.test.fixtures.maven.ModuleDescriptor

class RootMetaData implements IntegrityChecker{
    MavenItem metaDataItem
    DefaultMavenMetaData mavenMetaData

    RootMetaData(MavenItem mavenItem) {
        this.metaDataItem = mavenItem
        this.mavenMetaData = new DefaultMavenMetaData(mavenItem.file)
    }

    @Override
    void check(ModuleDescriptor moduleDescriptor) {
        metaDataItem.verify()
        assert mavenMetaData.artifactId == moduleDescriptor.moduleName
        assert mavenMetaData.groupId == moduleDescriptor.organisation
        assert mavenMetaData.version == moduleDescriptor.revision
        assert mavenMetaData.versions.contains(moduleDescriptor.revision)
        assert mavenMetaData.lastUpdated ==~ /\d{14}/
    }
}
