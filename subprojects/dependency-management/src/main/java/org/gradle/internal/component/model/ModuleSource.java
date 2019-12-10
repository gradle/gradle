/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.component.model;

/**
 * A memento for any resolution state that is relevant to locate the artifacts of a resolved module version.
 *
 * Implementations must retain as little state as possible and must be immutable. There are two different kinds
 * of module sources:
 *
 * <ul>
 *     <li>sources which can be reconstructed from the caches, for example, the repository source</li>
 *     <li>sources which can't be reconstructed because the cache format looses information, for example a descriptor converted to binary</li>
 * </ul>
 *
 * The 2d kind, sources which must be stored into the metadata cache, have to implement the {@link PersistentModuleSource}
 * interface instead.
 */
public interface ModuleSource {
}
