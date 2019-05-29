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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.ImplementationAwareTaskAction;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.lang.reflect.Method;

class StandardTaskAction implements ImplementationAwareTaskAction, Describable {
    private final Class<? extends Task> type;
    private final Method method;

    public StandardTaskAction(Class<? extends Task> type, Method method) {
        this.type = type;
        this.method = method;
    }

    @Override
    public void execute(Task task) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(method.getDeclaringClass().getClassLoader());
        try {
            doExecute(task, method.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    protected void doExecute(Task task, String methodName) {
        JavaMethod.of(task, Object.class, methodName).invoke(task);
    }

    @Override
    public ImplementationSnapshot getActionImplementation(ClassLoaderHierarchyHasher hasher) {
        return ImplementationSnapshot.of(type.getName(), hasher.getClassLoaderHash(method.getDeclaringClass().getClassLoader()));
    }

    @Override
    public String getDisplayName() {
        return "Execute " + method.getName();
    }
}
