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
package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.Project;
import org.jspecify.annotations.NullMarked;

/**
 * Tells whether the sources of a project form a Java module, which decides if a project dependency
 * on it goes on the module path.
 *
 * <p>Resolving the Eclipse classpath of a project dependency needs this answer about the
 * <em>depended-upon</em> project. Reading it directly from that project is a cross-project access
 * and is not allowed with Isolated Projects enabled, so the model builders supply an implementation
 * backed by values gathered per project up front.
 */
@NullMarked
public interface ProjectModulePathResolver {

    /**
     * Resolves the flag for the given project, which may be a project other than the one whose
     * classpath is being built.
     */
    boolean isInferModulePath(Project project);
}
