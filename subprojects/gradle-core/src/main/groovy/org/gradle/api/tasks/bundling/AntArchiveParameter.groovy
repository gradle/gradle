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

import org.apache.commons.lang.builder.EqualsBuilder
import org.apache.commons.lang.builder.HashCodeBuilder

/**
 * @author Hans Dockter
 */
class AntArchiveParameter {
    List resourceCollections
    List mergeFileSets
    List mergeGroupFileSets
    boolean createIfEmpty
    File destinationDir
    String archiveName
    AntBuilder ant

    AntArchiveParameter(List resourceCollections, List mergeFileSets, List mergeGroupFileSets, boolean createIfEmpty, File destinationDir, String archiveName, AntBuilder ant) {
        this.resourceCollections = resourceCollections
        this.mergeFileSets = mergeFileSets
        this.mergeGroupFileSets = mergeGroupFileSets
        this.createIfEmpty = createIfEmpty
        this.destinationDir = destinationDir
        this.archiveName = archiveName
        this.ant = ant
    }

    String emptyPolicy() {
        createIfEmpty ? AbstractAntArchive.EMPTY_POLICY_CREATE : AbstractAntArchive.EMPTY_POLICY_SKIP
    }

    boolean equals(object) {
        EqualsBuilder.reflectionEquals(this, object)
    }

    int hashCode() {
        HashCodeBuilder.reflectionHashCode(this)
    }
}
