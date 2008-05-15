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

package org.gradle.api.internal

import org.gradle.api.GradleScriptException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.PathOrder
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.StopExecutionException
import org.gradle.util.GradleUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class DefaultTask implements Task {
    private static Logger logger = LoggerFactory.getLogger(DefaultTask)

    AntBuilder ant = new AntBuilder()

    Project project

    String name

    List actions = []

    List skipProperties = []

    Set dependsOn = []

    boolean executed

    boolean lateInitialized = false

    List lateInitalizeClosures = []

    List afterDagClosures = []

    DefaultTask() {

    }

    DefaultTask(Project project, String name) {
        assert project
        assert name
        this.project = project
        this.name = name
    }

    Task doFirst(Closure action) {
        if (!action) {throw new InvalidUserDataException('Action must not be null!')}
        actions.add(0, action)
        this
    }

    Task doLast(Closure action) {
        if (!action) {throw new InvalidUserDataException('Action must not be null!')}
        actions << action
        this
    }

    Task deleteAllActions() {
        actions = []
        this
    }

    void execute() {
        logger.debug("Executing ProjectTarget: $path")
        List trueSkips = (skipProperties + ["$Task.AUTOSKIP_PROPERTY_PREFIX$name"]).findAll {String prop -> Boolean.getBoolean(prop)}
        if (trueSkips) {
            logger.info("Skipping execution as following skip properties are true: ${trueSkips.join(' ')}")
        } else {
            for (action in actions) {
                logger.debug("Executing Action:")
                try {
                    action.call(this)
                } catch (StopExecutionException e) {
                    logger.info("Execution stopped by some action with message: $e.message")
                    break
                } catch (StopActionException e) {
                    logger.debug("Action stopped by some action with message: $e.message")
                    continue
                } catch (Throwable t) {
                    throw new GradleScriptException(t, project?.buildScriptFinder?.buildFileName ?: 'unknown')
                }
            }
        }
        executed = true
    }

    String getPath() {
        String separator = project.is(project.rootProject) ? '' : Project.PATH_SEPARATOR
        project.path + separator + name
    }

    public boolean equals(Object other) {
        path.equals(other.path)
    }

    public int hashCode() {
        return path.hashCode();
    }

    public int compareTo(Object other) {
        PathOrder.compareTo(path, other.path)
    }

    String toString() {
        getPath()
    }

    Task dependsOn(Object[] paths) {
        paths.each {path ->
            if (!path) {
                throw new InvalidUserDataException('A pathelement must not be empty')
            }
            dependsOn << path
        }
        this
    }

    Task configure(Closure closure) {
        GradleUtil.configure(closure, this)
    }

    Task lateInitialize(Closure closure) {
        lateInitalizeClosures << closure
        this
    }

    Task afterDag(Closure closure) {
        afterDagClosures << closure
        this
    }

    Task applyLateInitialize() {
        Task task = configureEvent(lateInitalizeClosures)
        lateInitialized = true
        task
    }

    Task applyAfterDagClosures() {
        configureEvent(afterDagClosures)
    }

    private Task configureEvent(List closures) {
        closures.each {configure(it)}
        this
    }

    boolean getLateInitialized() {
        lateInitialized
    }

    boolean getExecuted() {
        executed
    }

}