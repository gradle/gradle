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

package org.gradle.api.internal.initialization;

import org.gradle.internal.code.Locality;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Knows who owns the code of the build: Gradle itself, the build, or a third party.
 * <p>
 * Script classpath resolution records which jars come from the build and which from a repository.
 * A class is then looked up through the jar it was loaded from. Gradle's own classes are known by their class loader.
 * Gradle claims a locality only when a record says so; otherwise the locality is unknown.
 */
@ServiceScope(Scope.BuildTree.class)
public interface CodeLocalityRegistry {

    /**
     * Records a classpath entry and who owns it.
     */
    void recordClasspathEntry(File file, Locality locality);

    /**
     * Returns who owns the given class.
     */
    Locality localityOf(Class<?> clazz);
}
