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
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;

/**
 * Registry of services provided at execution time for already configured projects.
 */
public class ProjectExecutionServiceRegistry implements AutoCloseable {
    private final NodeExecutionContext global;
    private final LoadingCache<ProjectInternal, NodeExecutionContext> projectRegistries = CacheBuilder.newBuilder()
        .build(new CacheLoader<ProjectInternal, NodeExecutionContext>() {
            @Override
            public NodeExecutionContext load(@Nonnull ProjectInternal project) {
                return new DefaultNodeExecutionContext(new ProjectExecutionServices(project));
            }
        });

    public ProjectExecutionServiceRegistry(ServiceRegistry globalServices) {
        global = globalServices::get;
    }

    public NodeExecutionContext forProject(@Nullable ProjectInternal project) {
        if (project == null) {
            return global;
        }
        return projectRegistries.getUnchecked(project);
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(projectRegistries.asMap().values()).stop();
    }

    private static class DefaultNodeExecutionContext implements NodeExecutionContext, Closeable {
        private final ProjectExecutionServices services;

        public DefaultNodeExecutionContext(ProjectExecutionServices services) {
            this.services = services;
        }

        @Override
        public <T> T getService(Class<T> type) throws ServiceLookupException {
            return services.get(type);
        }

        @Override
        public void close() throws IOException {
            services.close();
        }
    }
}
