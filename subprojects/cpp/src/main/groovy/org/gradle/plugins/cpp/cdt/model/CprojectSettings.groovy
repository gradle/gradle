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
package org.gradle.plugins.cpp.cdt.model

import org.gradle.plugins.binaries.model.Binary
import org.gradle.plugins.binaries.model.HeaderExportingSourceSet

/**
 * Exposes a more logical view of the actual .cproject descriptor file
 */
class CprojectSettings {

    Binary binary

    void applyTo(CprojectDescriptor descriptor) {
        if (binary) { 
            applyBinaryTo(descriptor) 
        } else {
            throw new IllegalStateException("no binary set")
        }
    }

    private applyBinaryTo(CprojectDescriptor descriptor) {
        def includeRoots = getProjectRelativeIncludeRoots()

        descriptor.rootCppCompilerTools.each { compiler ->
            def includePathsOption = descriptor.getOrCreateIncludePathsOption(compiler)
            includePathsOption.children().each { includePathsOption.remove(it) }
            includeRoots.each { includeRoot ->
                includePathsOption.appendNode("listOptionValue", [builtIn: "false", value: "\"\${workspace_loc:/\${ProjName}/$includeRoot\""])
            }
        }
    }
    
    Set<File> getIncludeRoots() {
        def includeRoots = new LinkedHashSet()
        binary.sourceSets.withType(HeaderExportingSourceSet).each {
            includeRoots.addAll(it.exportedHeaders.srcDirs)
        }
        includeRoots
    }

    Set<String> getProjectRelativeIncludeRoots() {
        getIncludeRoots().collect { binary.project.relativePath(it) }
    }
}