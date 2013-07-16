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
package org.gradle.internal.filestore;

import org.gradle.api.Action;
import org.gradle.internal.resource.local.LocallyAvailableResource;

import java.io.File;

public interface FileStore<K> {

    LocallyAvailableResource move(K key, File source);

    LocallyAvailableResource copy(K key, File source);

    void moveFilestore(File destination);

    LocallyAvailableResource add(K key, Action<File> addAction);
}
