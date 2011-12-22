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
package org.gradle.api.plugins.quality

import org.gradle.api.Project

/**
 * Extension specifying options for
 * the FindBugs plugin.
 * 
 * @see FindBugsPlugin
 */
class FindBugsExtension {
    private Project project
    
    /**
     * The name of the directory to use for
     * FindBugs results.
     */
    String resultsDirName
    
    /**
     * Creates a convention instance tied
     * to the specified project.
     * 
     * Defaults the {@code resultsDirName} to "findbugs"
     * @param project
     */
    FindBugsExtension(Project project) {
        this.project = project
        resultsDirName = 'findbugs'
    }
    
    /**
     * Gets the directory to be used for FindBugs results. This is determined
     * using the {@code resultsDirName} property, evaluated relative to the
     * project's build directory.
     * @return the results dir for FindBugs
     */
    File getResultsDir() {
        return project.fileResolver.withBaseDir(project.buildDir).resolve(resultsDirName)
    }
}
