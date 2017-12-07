/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.tasks.properties.PropertiesWalker;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@NonNullApi
public class DefaultTaskLocalState implements TaskLocalStateInternal {
    private final FileResolver resolver;
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final PropertiesWalker propertiesWalker;
    private final PropertySpecFactory specFactory;
    private final List<Object> paths = Lists.newArrayList();

    public DefaultTaskLocalState(FileResolver resolver, TaskInternal task, TaskMutator taskMutator, PropertiesWalker propertiesWalker, PropertySpecFactory specFactory) {
        this.resolver = resolver;
        this.task = task;
        this.taskMutator = taskMutator;
        this.propertiesWalker = propertiesWalker;
        this.specFactory = specFactory;
    }

    @Override
    public void register(final Object... paths) {
        taskMutator.mutate("TaskLocalState.register(Object...)", new Runnable() {
            @Override
            public void run() {
                Collections.addAll(DefaultTaskLocalState.this.paths, paths);
            }
        });
    }

    @Override
    public void visitRuntimeProperties(PropertyVisitor visitor) {
        for (Object path : paths) {
            visitor.visitLocalStateProperty(path);
        }
    }

    private void visitAllProperties(PropertyVisitor visitor) {
        propertiesWalker.visitProperties(specFactory, visitor, task);
        visitRuntimeProperties(visitor);
    }

    @Override
    public FileCollection getFiles() {
        Iterable<Object> objects = new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                GetFilesVisitor visitor = new GetFilesVisitor();
                visitAllProperties(visitor);
                return visitor.getLocalState().iterator();
            }
        };
        return new DefaultConfigurableFileCollection(task + " local state", resolver, null, objects);
    }

    @Override
    public GetFilesVisitor getFilesVisitor() {
        return new GetFilesVisitor();
    }

    public class GetFilesVisitor extends PropertyVisitor.Adapter implements TaskLocalStateInternal.GetFilesVisitor {
        private List<Object> localState = new ArrayList<Object>();

        @Override
        public void visitLocalStateProperty(Object value) {
            localState.add(value);
        }

        public List<Object> getLocalState() {
            return localState;
        }

        public FileCollection getFiles() {
            return new DefaultConfigurableFileCollection(task + " local state", resolver, null, getLocalState());
        }
    }
}
