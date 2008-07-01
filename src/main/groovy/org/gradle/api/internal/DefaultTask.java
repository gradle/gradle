/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.util.GroovyJavaHelper;
import org.gradle.util.ConfigureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import groovy.lang.Closure;

import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultTask extends AbstractTask {
    private static Logger logger = LoggerFactory.getLogger(DefaultTask.class);

    public DefaultTask() {

    }

    public DefaultTask(Project project, String name) {
        super(project, name);
    }

    public Task doFirst(Closure action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        doFirst(GroovyJavaHelper.closureToAction(action));
        return this;
    }

    public Task doLast(Closure action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        doLast(GroovyJavaHelper.closureToAction(action));
        return this;
    }

    public Task configure(Closure closure) {
        return (Task) ConfigureUtil.configure(closure, this);
    }

    public Task lateInitialize(Closure closure) {
        lateInitalizeClosures.add(closure);
        return this;
    }

    public Task afterDag(Closure closure) {
        afterDagClosures.add(closure);
        return this;
    }

    public Task applyLateInitialize() {
        Task task = configureEvent(lateInitalizeClosures);
        lateInitialized = true;
        return task;
    }

    public Task applyAfterDagClosures() {
        return configureEvent(afterDagClosures);
    }

    private Task configureEvent(List<Closure> closures) {
        for (Closure closure : closures) {
            configure(closure);
        }
        return this;
    }

    public boolean getLateInitialized() {
        return lateInitialized;
    }

    public boolean getExecuted() {
        return executed;
    }

}