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
import org.gradle.internal.extensibility.ConventionAwareHelper;
import org.gradle.work.DisableCachingByDefault;

import java.util.concurrent.Callable;

@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class ConventionTask extends DefaultTask implements IConventionAware {
    private ConventionMapping conventionMapping;

    public Task conventionMapping(String property, Callable<?> mapping) {
        getConventionMapping().map(property, mapping);
        return this;
    }

    public Task conventionMapping(String property, Closure mapping) {
        getConventionMapping().map(property, mapping);
        return this;
    }

    @Override
    @Internal
    @SuppressWarnings("deprecation")
    public ConventionMapping getConventionMapping() {
        if (conventionMapping == null) {
            conventionMapping = new ConventionAwareHelper(this, getConvention());
        }
        return conventionMapping;
    }
}
