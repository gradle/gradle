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

package org.gradle.api.plugins.migration.fixtures.gradle

import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.internal.migration.FileBuildOutcome
import org.gradle.tooling.model.internal.migration.ProjectOutcomes
import org.gradle.util.ConfigureUtil

class MutableProjectOutcomes implements ProjectOutcomes {
    String name
    String description
    String path
    MutableProjectOutcomes parent
    DomainObjectSet<MutableProjectOutcomes> children = new MutableDomainObjectSet()
    File projectDirectory
    DomainObjectSet<FileBuildOutcome> fileOutcomes = new MutableDomainObjectSet()

    MutableProjectOutcomes createChild(String childName, Closure c = {}) {
        def mpo = new MutableProjectOutcomes()
        mpo.parent = this
        mpo.name = childName
        mpo.path = parent ? "$path:$childName" : ":$childName"
        mpo.projectDirectory = new File(projectDirectory, childName)
        mpo.description = "project $mpo.path"

        children << mpo
        ConfigureUtil.configure(c, mpo)
    }

    FileBuildOutcome addFile(String archivePath, String typeIdentifier = null, String taskName = archivePath) {
        def outcome = new FileBuildOutcome() {
            File getFile() {
                new File(projectDirectory, archivePath)
            }

            String getTaskPath() {
                "$path:$taskName"
            }

            String getTypeIdentifier() {
                typeIdentifier
            }
        }
        fileOutcomes << outcome
        outcome
    }
}