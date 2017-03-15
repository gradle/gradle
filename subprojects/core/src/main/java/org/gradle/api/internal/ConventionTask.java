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

import groovy.lang.Closure;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.Internal;

import java.util.concurrent.Callable;

public abstract class ConventionTask extends DefaultTask implements IConventionAware {
    private final ConventionMapping conventionMapping;

    protected ConventionTask() {
        conventionMapping = new ConventionAwareHelper(this, getProject().getConvention());
    }

    public Task conventionMapping(String property, Callable<?> mapping) {
        conventionMapping.map(property, mapping);
        return this;
    }

    public Task conventionMapping(String property, Closure mapping) {
        conventionMapping.map(property, mapping);
        return this;
    }

    @Override
    @Internal
    public ConventionMapping getConventionMapping() {
        return conventionMapping;
    }
}
