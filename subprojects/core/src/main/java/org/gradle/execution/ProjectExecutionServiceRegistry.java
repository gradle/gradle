/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.execution;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.api.internal.project.ProjectInternal;

import javax.annotation.Nonnull;

/**
 * Registry of services provided for already configured projects.
 */
public class ProjectExecutionServiceRegistry implements AutoCloseable {
    private final LoadingCache<ProjectInternal, ProjectExecutionServices> projectRegistries = CacheBuilder.newBuilder()
        .build(new CacheLoader<ProjectInternal, ProjectExecutionServices>() {
            @Override
            public ProjectExecutionServices load(@Nonnull ProjectInternal project) {
                return new ProjectExecutionServices(project);
            }
        });

    public <T> T getProjectService(ProjectInternal project, Class<T> serviceType) {
        return projectRegistries.getUnchecked(project).get(serviceType);
    }

    @Override
    public void close() {
        for (ProjectExecutionServices registry : projectRegistries.asMap().values()) {
            registry.close();
        }
    }
}
