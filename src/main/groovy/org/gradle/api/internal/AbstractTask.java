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
package org.gradle.api.internal;

import org.gradle.api.*;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.StopActionException;
import org.gradle.util.GUtil;
import org.gradle.execution.Dag;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.codehaus.groovy.runtime.InvokerInvocationException;

import java.util.*;

import groovy.util.AntBuilder;
import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public abstract class AbstractTask implements Task {
    private static Logger logger = LoggerFactory.getLogger(AbstractTask.class);

    AntBuilder ant = new AntBuilder();

    Project project;

    Dag tasksGraph;

    String name;

    List<TaskAction> actions = new ArrayList<TaskAction>();

    List skipProperties = new ArrayList();

    Set dependsOn = new HashSet();

    boolean executed;

    boolean enabled = true;

    String path = null;

    boolean dagNeutral = false;

    public AbstractTask() {

    }

    public AbstractTask(Project project, String name, Dag tasksGraph) {
        assert project != null;
        assert name != null;
        this.project = project;
        this.name = name;
        this.tasksGraph = tasksGraph;
        String separator = project == (project.getRootProject()) ? "" : Project.PATH_SEPARATOR;
        path = project.getPath() + separator + name;
    }

    public AntBuilder getAnt() {
        return ant;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List getActions() {
        return actions;
    }

    public void setActions(List actions) {
        this.actions = actions;
    }

    public List<String> getSkipProperties() {
        return skipProperties;
    }

    public void setSkipProperties(List<String> skipProperties) {
        this.skipProperties = skipProperties;
    }

    public Set getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(Set dependsOn) {
        this.dependsOn = dependsOn;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public boolean isDagNeutral() {
        return dagNeutral;
    }

    public void setDagNeutral(boolean dagNeutral) {
        this.dagNeutral = dagNeutral;
    }

    public Dag getTasksGraph() {
        return tasksGraph;
    }

    public void setTasksGraph(Dag tasksGraph) {
        this.tasksGraph = tasksGraph;
    }

    public Task deleteAllActions() {
        actions = new ArrayList<TaskAction>();
        return this;
    }

    public void execute() {
        logger.debug("Executing Task: {}", path);
        if (!enabled) {
            logger.info("Skipping execution as task is disabled.");
            executed = true;
            return;
        }
        List trueSkips = new ArrayList();
        List<String> allSkipProperties = new ArrayList<String>(skipProperties);
        allSkipProperties.add(Task.AUTOSKIP_PROPERTY_PREFIX + name);
        for (String skipProperty : allSkipProperties) {
            String propValue = System.getProperty(skipProperty);
            if (propValue != null && !(propValue.toUpperCase().equals("FALSE"))) {
                trueSkips.add(skipProperty);
            }
        }
        if (trueSkips.size() > 0) {
            logger.info("Skipping execution as following skip properties are true: " + trueSkips);
        } else {
            for (TaskAction action : actions) {
                logger.debug("Executing Action:");
                try {
                    action.execute(this, getTasksGraph());
                } catch (StopExecutionException e) {
                    logger.info("Execution stopped by some action with message: {}", e.getMessage());
                    break;
                } catch (StopActionException e) {
                    logger.debug("Action stopped by some action with message: {}", e.getMessage());
                    continue;
                    // todo Due to a Groovy bug which wraps Exceptions from Java classes into InvokerInvocationExceptions we have to do this. After the grrovy2java refactoring we can remove this.
                } catch (InvokerInvocationException e) {
                    if (e.getCause() != null) {
                        if (e.getCause() instanceof StopActionException) {
                            continue;
                        } else if (e.getCause() instanceof StopExecutionException) {
                            break;
                        } else if (e.getCause() instanceof GradleException) {
                            ((GradleException) e.getCause()).setScriptName(project.getBuildFileCacheName());
                        } else {
                            throw new GradleScriptException(e.getCause(), project.getBuildFileCacheName());
                        }
                    }
                    throw e;
                } catch (GradleException e) {
                    ((GradleException) e).setScriptName(project.getBuildFileCacheName());
                    throw e;
                } catch (Throwable t) {
                    throw new GradleScriptException(t, project.getBuildFileCacheName());
                }
            }
        }
        executed = true;
    }

    public Task dependsOn(Object... paths) {
        for (Object path : paths) {
            if (!GUtil.isTrue(path)) {
                throw new InvalidUserDataException("A pathelement must not be empty");
            }
            dependsOn.add(path);
        }
        return this;
    }

    public Task doFirst(TaskAction action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        actions.add(0, action);
        return this;
    }

    public Task doLast(TaskAction action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        actions.add(action);
        return this;
    }

    public boolean equals(Object other) {
        AbstractTask otherTask = (AbstractTask) other;
        return getPath().equals(otherTask.getPath());
    }

    public int hashCode() {
        return path.hashCode();
    }

    public int compareTo(Object other) {
        AbstractTask otherTask = (AbstractTask) other;
        int depthCompare = project.depthCompare(otherTask.project);
        if (depthCompare == 0) {
            return getPath().compareTo(otherTask.getPath());
        } else {
            return depthCompare;
        }
    }

    public String toString() {
        return getPath();
    }
}
