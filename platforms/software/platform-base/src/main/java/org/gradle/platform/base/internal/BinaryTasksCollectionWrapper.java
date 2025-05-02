/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Task;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.internal.DelegatingDomainObjectSet;
import org.gradle.platform.base.BinaryTasksCollection;

public class BinaryTasksCollectionWrapper extends DelegatingDomainObjectSet<Task> implements BinaryTasksCollection {

    public BinaryTasksCollectionWrapper(BinaryTasksCollection delegate) {
        super(delegate);
    }

    public <T extends Task> T findSingleTaskWithType(Class<T> type) {
        DomainObjectSet<T> tasks = withType(type);
        if (tasks.size() == 0) {
            return null;
        }
        if (tasks.size() > 1) {
            throw new UnknownDomainObjectException(String.format("Multiple tasks with type '%s' found.", type.getSimpleName()));
        }
        return tasks.iterator().next();
    }

    @Override
    protected BinaryTasksCollection getDelegate() {
        return (BinaryTasksCollection) super.getDelegate();
    }

    @Override
    public String taskName(String verb) {
        return getDelegate().taskName(verb);
    }

    @Override
    public String taskName(String verb, String object) {
        return getDelegate().taskName(verb, object);
    }

    @Override
    public Task getBuild() {
        return getDelegate().getBuild();
    }

    @Override
    public Task getCheck() {
        return getDelegate().getCheck();
    }

    @Override
    public <T extends Task> void create(String name, Class<T> type, Action<? super T> config) {
        getDelegate().create(name, type, config);
    }

}
