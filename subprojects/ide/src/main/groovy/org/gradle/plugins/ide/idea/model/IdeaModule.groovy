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

package org.gradle.plugins.ide.idea.model

/**
 * Author: Szczepan Faber, created at: 3/31/11
 */
class IdeaModule {

    /**
     * Idea module name; controls the name of the *.iml file
     */
    String name

    /**
     * The directories containing the production sources.
     */
    Set<File> sourceDirs

    protected Set<Path> getSourcePaths(PathFactory pathFactory) {
        getSourceDirs().findAll { it.exists() }.collect { pathFactory.path(it) }
    }
}
