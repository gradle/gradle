/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.binaries.model.internal

import org.gradle.plugins.binaries.model.Library
import org.gradle.plugins.binaries.model.HeaderExportingSourceSet

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.project.ProjectInternal

class DefaultLibrary extends DefaultBinary implements Library {

    final SourceDirectorySet headers

    DefaultLibrary(String name, ProjectInternal project) {
        super(name, project)
        this.headers = new DefaultSourceDirectorySet("headers", String.format("Exported headers for native library '%s'", name), project.getFileResolver())

        initExportedHeaderTracking()
    }

    protected initExportedHeaderTracking() {
        def headerExportingSourceSets = sourceSets.withType(HeaderExportingSourceSet)
        def updateHeadersSourceDirectorySet = { headers.srcDirs = headerExportingSourceSets*.exportedHeaders }

        headerExportingSourceSets.all(updateHeadersSourceDirectorySet)
        headerExportingSourceSets.whenObjectRemoved(updateHeadersSourceDirectorySet)
    }

}