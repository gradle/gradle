/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.events.task.internal;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.PluginIdentifier;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.model.internal.Exceptions;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Implementation of the {@code TaskOperationDescriptor} interface.
 */
public final class DefaultTaskOperationDescriptor extends DefaultOperationDescriptor implements TaskOperationDescriptor {

    private static final String DEPENDENCIES_METHOD = TaskOperationDescriptor.class.getSimpleName() + ".getDependencies()";
    private static final String ORIGIN_PLUGIN_METHOD = TaskOperationDescriptor.class.getSimpleName() + ".getOriginPlugin()";

    private final String taskPath;
    private final Supplier<Set<OperationDescriptor>> dependencies;
    private final Supplier<PluginIdentifier> originPlugin;

    public DefaultTaskOperationDescriptor(InternalTaskDescriptor descriptor, OperationDescriptor parent, String taskPath) {
        this(descriptor, parent, taskPath, unsupportedDependencies(), unsupportedOriginPlugin());
    }

    public DefaultTaskOperationDescriptor(InternalTaskDescriptor descriptor, OperationDescriptor parent, String taskPath, Set<OperationDescriptor> dependencies, @Nullable PluginIdentifier originPlugin) {
        this(descriptor, parent, taskPath, Suppliers.ofInstance(dependencies), Suppliers.ofInstance(originPlugin));
    }

    private DefaultTaskOperationDescriptor(InternalTaskDescriptor descriptor, OperationDescriptor parent, String taskPath, Supplier<Set<OperationDescriptor>> dependencies, Supplier<PluginIdentifier> originPlugin) {
        super(descriptor, parent);
        this.taskPath = taskPath;
        this.dependencies = dependencies;
        this.originPlugin = originPlugin;
    }

    @Override
    public String getTaskPath() {
        return taskPath;
    }

    @Override
    public Set<? extends OperationDescriptor> getDependencies() {
        return dependencies.get();
    }

    @Override
    @Nullable
    public PluginIdentifier getOriginPlugin() {
        return originPlugin.get();
    }

    private static Supplier<PluginIdentifier> unsupportedOriginPlugin() {
        return unsupportedMethodExceptionThrowingSupplier(ORIGIN_PLUGIN_METHOD);
    }

    private static Supplier<Set<OperationDescriptor>> unsupportedDependencies() {
        return unsupportedMethodExceptionThrowingSupplier(DEPENDENCIES_METHOD);
    }

    private static <T> Supplier<T> unsupportedMethodExceptionThrowingSupplier(final String method) {
        return new Supplier<T>() {
            @Override
            public T get() {
                throw Exceptions.unsupportedMethod(method);
            }
        };
    }

}
