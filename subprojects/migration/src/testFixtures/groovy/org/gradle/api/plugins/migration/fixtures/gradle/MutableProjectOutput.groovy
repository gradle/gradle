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
import org.gradle.tooling.model.internal.migration.Archive
import org.gradle.tooling.model.internal.migration.ProjectOutput
import org.gradle.util.ConfigureUtil
import org.gradle.tooling.model.internal.migration.TestRun

class MutableProjectOutput implements ProjectOutput {
    String name
    String description
    String path
    MutableProjectOutput parent
    DomainObjectSet<MutableProjectOutput> children = new MutableDomainObjectSet()
    File projectDirectory
    String gradleVersion
    DomainObjectSet<Archive> archives = new MutableDomainObjectSet()
    DomainObjectSet<TestRun> testRuns = new MutableDomainObjectSet()

    MutableProjectOutput createChild(String childName, Closure c = {}) {
        def mpo = new MutableProjectOutput()
        mpo.parent = this
        mpo.gradleVersion = gradleVersion
        mpo.name = childName
        mpo.path = parent ? "$path:$childName" : ":$childName"
        mpo.projectDirectory = new File(projectDirectory, childName)
        mpo.description = "project $mpo.path"

        children << mpo
        ConfigureUtil.configure(c, mpo)
    }

    Archive addArchive(String taskName, String archivePath = taskName) {
        def archive = new Archive() {
            File getFile() {
                new File(projectDirectory, archivePath)
            }

            String getTaskPath() {
                "$path:$archivePath"
            }
        }
        archives << archive
        archive
    }
}