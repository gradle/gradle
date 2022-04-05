/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.file.FileCollection
import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.Scopes
import java.io.File


@EventScope(Scopes.BuildTree::class)
interface UndeclaredBuildInputListener {
    /**
     * Called when an undeclared system property read happens.
     */
    fun systemPropertyRead(key: String, value: Any?, consumer: String?)

    fun envVariableRead(key: String, value: String?, consumer: String?)

    fun fileOpened(file: File, consumer: String?)

    fun fileCollectionObserved(fileCollection: FileCollection, consumer: String)
}
