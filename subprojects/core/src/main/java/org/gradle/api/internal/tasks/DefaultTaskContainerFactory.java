/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks;

import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.internal.project.CrossProjectModelAccess;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskIdentityFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;

public class DefaultTaskContainerFactory implements Factory<TaskContainerInternal> {
    private final Instantiator instantiator;
    private final TaskIdentityFactory taskIdentityFactory;
    private final ITaskFactory taskFactory;
    private final CollectionCallbackActionDecorator callbackDecorator;
    private final ProjectInternal project;
    private final TaskStatistics statistics;
    private final BuildOperationRunner buildOperationRunner;
    private final CrossProjectConfigurator crossProjectConfigurator;
    private final CrossProjectModelAccess crossProjectModelAccess;
    private final UserCodeApplicationContext userCodeApplicationContext;

    public DefaultTaskContainerFactory(
        Instantiator instantiator,
        TaskIdentityFactory taskIdentityFactory,
        ITaskFactory taskFactory,
        ProjectInternal project,
        TaskStatistics statistics,
        BuildOperationRunner buildOperationRunner,
        CrossProjectConfigurator crossProjectConfigurator,
        CollectionCallbackActionDecorator callbackDecorator,
        UserCodeApplicationContext userCodeApplicationContext,
        CrossProjectModelAccess crossProjectModelAccess
    ) {
        this.instantiator = instantiator;
        this.taskIdentityFactory = taskIdentityFactory;
        this.taskFactory = taskFactory;
        this.project = project;
        this.statistics = statistics;
        this.buildOperationRunner = buildOperationRunner;
        this.crossProjectConfigurator = crossProjectConfigurator;
        this.callbackDecorator = callbackDecorator;
        this.crossProjectModelAccess = crossProjectModelAccess;
        this.userCodeApplicationContext = userCodeApplicationContext;
    }

    @Override
    public TaskContainerInternal create() {
        return instantiator.newInstance(
            DefaultTaskContainer.class,
            project,
            instantiator,
            taskIdentityFactory,
            taskFactory,
            statistics,
            buildOperationRunner,
            crossProjectConfigurator,
            callbackDecorator,
            crossProjectModelAccess,
            userCodeApplicationContext
        );
    }
}
