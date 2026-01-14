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

package org.gradle.internal.resource.local;

import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Receives events about accessing file resources through {@link ExternalResource}.
 * These events are not sent through {@code ListenerManager}.
 */
@ServiceScope(Scope.BuildTree.class)
public interface FileResourceListener {
    /**
     * An empty implementation that does nothing
     */
    FileResourceListener NO_OP = new FileResourceListener() {
        @Override
        public void fileObserved(File file) {}

        @Override
        public void directoryChildrenObserved(File file) {}
    };

    /**
     * Called when a file system resource is accessed as a regular file.
     */
    void fileObserved(File file);

    /**
     * Called when the children of a file system resource are listed.
     */
    void directoryChildrenObserved(File file);
}
