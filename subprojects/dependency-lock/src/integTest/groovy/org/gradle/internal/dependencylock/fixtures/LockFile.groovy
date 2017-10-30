/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.dependencylock.fixtures

import groovy.transform.TupleConstructor
import org.gradle.internal.dependencylock.converter.JsonDependencyLockConverter

class LockFile {

    private final Object json

    LockFile(Object json) {
        this.json = json
        assert getComment() == JsonDependencyLockConverter.USER_NOTICE
        assert getLockFileVersion() == JsonDependencyLockConverter.LOCK_FILE_VERSION
    }

    String getComment() {
        json._comment
    }

    String getLockFileVersion() {
        json.lockFileVersion
    }

    List<LockedDependency> getLocks(String projectPath, String configurationName) {
        def matchingProject = json.projects.find { it.path == projectPath }

        if (!matchingProject) {
            return []
        }

        def matchingConfiguration = matchingProject.configurations.find { it.name == configurationName }

        if (!matchingConfiguration) {
            return []
        }

        matchingConfiguration.dependencies.collect { new LockedDependency(it.moduleId, it.requestedVersion, it.lockedVersion) }
    }

    @TupleConstructor
    static class LockedDependency {
        final String moduleId
        final String requestedVersion
        final String lockedVersion

        String toString() {
            "${moduleId}:${requestedVersion} -> ${lockedVersion}"
        }
    }
}
