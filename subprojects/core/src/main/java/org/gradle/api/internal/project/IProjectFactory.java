/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.project;

import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

/**
 * Creates a {@link ProjectInternal} implementation.
 */
@ServiceScope(Scope.Build.class)
public interface IProjectFactory {
    ProjectInternal createProject(
        GradleInternal gradle,
        ProjectDescriptor projectDescriptor,
        ProjectState owner,
        @Nullable ProjectInternal parent,
        ServiceRegistryFactory serviceRegistryFactory,
        ClassLoaderScope selfClassLoaderScope,
        ClassLoaderScope baseClassLoaderScope
    );
}
