/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.api.tasks.AntBuilderAware
import org.gradle.api.tasks.util.FileSet

/**
 * @author Hans Dockter
 */
class AbstractAntArchive {
    static final String EMPTY_POLICY_SKIP = 'skip'

    static final String EMPTY_POLICY_CREATE = 'create'

    void addResourceCollections(List resourceCollections, delegate, String nodeName = null) {
        resourceCollections.each {AntBuilderAware antBuilderAware ->
            antBuilderAware.addToAntBuilder(delegate, nodeName)
        }
    }

    void addMetaArchiveParameter(AntMetaArchiveParameter parameter, delegate) {
        addResourceCollections(parameter.resourceCollections, delegate)
        parameter.gradleManifest?.addToAntBuilder(delegate)
        addResourceCollections(parameter.metaInfFileSets, delegate, 'metainf')
    }
}
