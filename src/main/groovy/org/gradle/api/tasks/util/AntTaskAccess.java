/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.util;

import groovy.lang.Closure;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;

/**
 * <p>Provides a mechanism to get access to an Ant Task object after
 * the task has been executed.  Use ant.project.addBuildListener to
 * add an AntTaskAccess object as a build listener.
 * </p>
 * @author Steve Appling
 */
public class AntTaskAccess implements BuildListener {
    Closure closure;

    /**
     * Default CTOR.  If this version is used, you must call {@link #setClosure(groovy.lang.Closure)}
     * before using this object.
     */
    public AntTaskAccess() {}

    /**
     * CTOR for normal use.  The provided closure will be called after
     * each task has finished.  The closure will be passed a single parameter,
     * the Task object of the task that just finished executing.
     * @param closure
     */
    public AntTaskAccess(Closure closure) {
        this.closure = closure;
    }

    public void setClosure(Closure closure) {
        this.closure = closure;
    }

    // implementation of BuildListener
    public void buildStarted(BuildEvent buildEvent) {
    }

    public void buildFinished(BuildEvent buildEvent) {
    }

    public void targetStarted(BuildEvent buildEvent) {
    }

    public void targetFinished(BuildEvent buildEvent) {
    }

    public void taskStarted(BuildEvent buildEvent) {
    }

    public void taskFinished(BuildEvent buildEvent) {
        closure.call(new Object[] {buildEvent.getTask()});
    }

    public void messageLogged(BuildEvent buildEvent) {
    }
}
