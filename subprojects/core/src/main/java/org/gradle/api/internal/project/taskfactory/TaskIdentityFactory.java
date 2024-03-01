/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.id.ConfigurationCacheableIdFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Task identity factory that ensures that unique ids are used correctly
 * when creating new instances and/or loading from the configuration cache.
 */
@ThreadSafe
@ServiceScope(Scopes.BuildTree.class)
public class TaskIdentityFactory {

    private final ConfigurationCacheableIdFactory idFactory;

    public TaskIdentityFactory(ConfigurationCacheableIdFactory idFactory) {
        this.idFactory = idFactory;
    }

    /**
     * Create a task identity.
     */
    public <T extends Task> TaskIdentity<T> create(String name, Class<T> type, ProjectInternal project) {
        long id = idFactory.createId();
        return doCreate(name, type, project, id);
    }

    /**
     * Recreate a task identity.
     * <p>
     * Should only be used when loading from the configuration cache to preserve task ids.
     */
    public <T extends Task> TaskIdentity<T> recreate(String name, Class<T> type, ProjectInternal project, long uniqueId) {
        idFactory.idRecreated();
        return doCreate(name, type, project, uniqueId);
    }

    private static <T extends Task> TaskIdentity<T> doCreate(String name, Class<T> type, ProjectInternal project, long uniqueId) {
        return new TaskIdentity<>(
            type,
            name,
            project.projectPath(name),
            project.identityPath(name),
            project.getGradle().getIdentityPath(),
            uniqueId
        );
    }

}
