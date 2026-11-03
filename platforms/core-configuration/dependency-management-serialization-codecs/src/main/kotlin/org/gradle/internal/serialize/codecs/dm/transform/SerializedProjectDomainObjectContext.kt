/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize.codecs.dm.transform

import org.gradle.internal.model.DomainObjectContext
import org.gradle.internal.model.ModelContainer
import org.gradle.util.Path


/**
 * The owner of project state loaded from the configuration cache, keeping the original
 * project's identity. This context has no mutable state, since the loaded state is isolated
 * and safe to access concurrently.
 */
class SerializedProjectDomainObjectContext(
    private val displayName: String,
    private val buildPath: Path
) : DomainObjectContext {

    override fun getDisplayName(): String = displayName

    override fun getBuildPath(): Path = buildPath

    override fun getModel(): ModelContainer<*> = ModelContainer.EMPTY
}
