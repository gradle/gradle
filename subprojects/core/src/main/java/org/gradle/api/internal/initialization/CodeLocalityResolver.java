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
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Tells who owns the code of a class: Gradle itself, the build, or a third party.
 * <p>
 * Gradle's own classes are known by their class loader. Any other class is asked of the class loader that loaded it,
 * which was built from a classpath that records where each entry came from. Gradle claims a locality only when such a
 * record exists; otherwise the locality is unknown.
 */
@ServiceScope(Scope.BuildTree.class)
public interface CodeLocalityResolver {

    Locality localityOf(Class<?> clazz);

    /**
     * The jar or class directory the class was loaded from, or null when it has none, such as a class defined in a script held in memory.
     */
    @Nullable
    File codeLocationOf(Class<?> clazz);
}
