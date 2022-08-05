/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.api.file.FileCollection;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.util.Set;

@ServiceScope(Scopes.Gradle.class)
public interface BuildOutputCleanupRegistry {

    /**
     * Registers outputs to be cleaned up as {@link org.gradle.api.Project#files(Object...)}.
     */
    void registerOutputs(Object files);

    /**
     * Determines if an output file is owned by this build and therefore can be safely removed.
     *
     * A file is owned by the build if it is registered as an output directly or within a directory registered as an output.
     */
    boolean isOutputOwnedByBuild(File file);

    /**
     * Finalizes the registered build outputs.
     *
     * After this call, it is impossible to register more outputs.
     */
    void resolveOutputs();

    /**
     * Gets the set of registered outputs as file collections.
     */
    Set<FileCollection> getRegisteredOutputs();
}
