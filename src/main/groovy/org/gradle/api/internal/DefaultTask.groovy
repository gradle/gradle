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

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.util.ConfigureUtil;

/**
 * @author Hans Dockter
 */
class DefaultTask extends AbstractTask {
    DefaultTask() {
        super();
    }

    DefaultTask(Project project, String name) {
        super(project, name);
    }

    Task doFirst(Closure action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        doFirst(convertClosureToAction(action));
        return this;
    }

    Task doLast(Closure action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        doLast(convertClosureToAction(action));
        return this;
    }

    Task configure(Closure closure) {
        return (Task) ConfigureUtil.configure(closure, this);
    }

    private TaskAction convertClosureToAction(final Closure actionClosure) {
        actionClosure.setDelegate(getProject());
        actionClosure.setResolveStrategy(Closure.OWNER_FIRST);
        actionClosure as TaskAction
    }

    def property(String name) {
        if (this.metaClass.hasProperty(this, name)) {
            return this.metaClass.getProperty(this, name)
        }
        return propertyMissing(name);
    }

    def propertyMissing(String name) {
        if (additionalProperties.keySet().contains(name)) {
            return additionalProperties[name]
        }
        throw new MissingPropertyException("Property '$name' not found for task $path.")
    }

    boolean hasProperty(String name) {
        if (this.metaClass.hasProperty(this, name)) {return true}
        if (additionalProperties.keySet().contains(name)) {return true}
        false
    }

    void defineProperty(String name, Object value) {
        if (this.metaClass.hasProperty(this, name)) {
            this.metaClass.setProperty(this, name, value)
            return
        }
        additionalProperties[name] = value
    }

    void setProperty(String name, Object value) {
        defineProperty(name, value)
    }
}