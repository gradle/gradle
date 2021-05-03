/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.compiler.java.listeners.constants;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ConstantsCollector implements TaskListener {

    private final JavacTask task;
    private final Map<String, Collection<String>> mapping;
    private final ConstantDependentsConsumer constantDependentsConsumer;

    public ConstantsCollector(JavacTask task, ConstantDependentsConsumer constantDependentsConsumer) {
        this.task = task;
        this.mapping = new HashMap<>();
        this.constantDependentsConsumer = constantDependentsConsumer;
    }

    public Map<String, Collection<String>> getMapping() {
        return mapping;
    }

    @Override
    public void started(TaskEvent e) {

    }

    @Override
    public void finished(TaskEvent e) {
        if (e.getKind() == Kind.ANALYZE) {
            Trees trees = Trees.instance(task);
            ConstantsTreeVisitor visitor = new ConstantsTreeVisitor(task.getElements(), trees, constantDependentsConsumer);
            TreePath path = trees.getPath(e.getCompilationUnit(), e.getCompilationUnit());
            visitor.scan(path, null);
        }
    }

}
