/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.base.serialize

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.cc.base.services.ProjectRefResolver
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.ownerService

/**
 * Writes a reference to a project.
 */
fun WriteContext.writeProjectRef(project: Project) {
    writeString(project.path)
}


/**
 * Reads a reference to a project. May block or throw if the projects haven't been loaded yet.
 *
 * @see ProjectRefResolver
 */
fun ReadContext.readProjectRef(): ProjectInternal {
    return ownerService<ProjectRefResolver>().getProject(readString())
}
