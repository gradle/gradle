/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.provider.model.internal;

import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DefaultToolingModelBuilderRegistry implements ToolingModelBuilderRegistry, ToolingModelBuilderLookup {
    private final ToolingModelBuilderLookup parent;

    private final List<ToolingModelBuilder> builders = new ArrayList<ToolingModelBuilder>();
    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectStateRegistry projectStateRegistry;

    public DefaultToolingModelBuilderRegistry(BuildOperationExecutor buildOperationExecutor, ProjectStateRegistry projectStateRegistry) {
        this(buildOperationExecutor, projectStateRegistry, null);
        register(new VoidToolingModelBuilder());
    }

    public DefaultToolingModelBuilderRegistry(BuildOperationExecutor buildOperationExecutor, ProjectStateRegistry projectStateRegistry, ToolingModelBuilderLookup parent) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.projectStateRegistry = projectStateRegistry;
        this.parent = parent;
    }

    @Override
    public void register(ToolingModelBuilder builder) {
        builders.add(builder);
    }

    @Override
    public ToolingModelBuilder getBuilder(String modelName) throws UnsupportedOperationException {
        ToolingModelBuilder builder = get(modelName);
        return new LenientToolingModelBuilder(builder);
    }

    @Override
    public Builder locateForClientOperation(String modelName, boolean parameter, GradleInternal target) throws UnknownModelException {
        ProjectInternal project = target.getDefaultProject();
        return new BuildOperationWrappingBuilder(new LockAllProjectsBuilder(locateForClientOperation(modelName, project, parameter), projectStateRegistry), modelName, project, buildOperationExecutor);
    }

    @Override
    public Builder locateForClientOperation(String modelName, boolean parameter, ProjectInternal target) throws UnknownModelException {
        return new BuildOperationWrappingBuilder(new LockSingleProjectBuilder(locateForClientOperation(modelName, target, parameter), target, projectStateRegistry), modelName, target, buildOperationExecutor);
    }

    private Builder locateForClientOperation(String modelName, ProjectInternal project, boolean parameter) throws UnknownModelException {
        ToolingModelBuilder builder = get(modelName);
        if (builder instanceof ParameterizedToolingModelBuilder) {
            return new BuilderWithParameter(modelName, project, (ParameterizedToolingModelBuilder) builder);
        }
        if (parameter) {
            throw new UnknownModelException(String.format("No parameterized builders are available to build a model of type '%s'.", modelName));
        }
        return new BuilderWithNoParameter(modelName, project, builder);
    }

    @Nullable
    public ToolingModelBuilder find(String modelName) {
        ToolingModelBuilder match = null;
        for (ToolingModelBuilder builder : builders) {
            if (builder.canBuild(modelName)) {
                if (match != null) {
                    throw new UnsupportedOperationException(String.format("Multiple builders are available to build a model of type '%s'.", modelName));
                }
                match = builder;
            }
        }
        if (match != null) {
            return match;
        }
        if (parent != null) {
            return parent.find(modelName);
        }
        return null;
    }

    private ToolingModelBuilder get(String modelName) {
        ToolingModelBuilder builder = find(modelName);
        if (builder != null) {
            return builder;
        }

        throw new UnknownModelException(String.format("No builders are available to build a model of type '%s'.", modelName));
    }

    private class LenientToolingModelBuilder implements ToolingModelBuilder {
        private final ToolingModelBuilder delegate;

        public LenientToolingModelBuilder(ToolingModelBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean canBuild(String modelName) {
            return delegate.canBuild(modelName);
        }

        @Override
        public Object buildAll(String modelName, Project project) {
            return projectStateRegistry.allowUncontrolledAccessToAnyProject(() -> delegate.buildAll(modelName, project));
        }
    }

    private static abstract class DelegatingBuilder implements Builder {
        final Builder delegate;

        public DelegatingBuilder(Builder delegate) {
            this.delegate = delegate;
        }

        @Override
        public Class<?> getParameterType() {
            return delegate.getParameterType();
        }
    }

    private static class BuilderWithNoParameter implements Builder {
        private final String modelName;
        private final ProjectInternal project;
        private final ToolingModelBuilder delegate;

        public BuilderWithNoParameter(String modelName, ProjectInternal project, ToolingModelBuilder delegate) {
            this.modelName = modelName;
            this.project = project;
            this.delegate = delegate;
        }

        @Override
        public Class<Object> getParameterType() {
            return null;
        }

        @Override
        public Object build(Object parameter) {
            if (parameter != null) {
                throw new IllegalArgumentException("Expected a null parameter");
            }
            return delegate.buildAll(modelName, project);
        }
    }

    private static class BuilderWithParameter implements Builder {
        private final String modelName;
        private final ProjectInternal project;
        private final ParameterizedToolingModelBuilder<?> delegate;

        public BuilderWithParameter(String modelName, ProjectInternal project, ParameterizedToolingModelBuilder<?> delegate) {
            this.modelName = modelName;
            this.project = project;
            this.delegate = delegate;
        }

        @Override
        public Class<?> getParameterType() {
            return delegate.getParameterType();
        }

        @Override
        public Object build(Object parameter) {
            if (parameter == null) {
                return delegate.buildAll(modelName, project);
            } else {
                return delegate.buildAll(modelName, Cast.uncheckedCast(parameter), project);
            }
        }
    }

    private static class LockSingleProjectBuilder extends DelegatingBuilder {
        private final ProjectInternal project;
        private final ProjectStateRegistry projectStateRegistry;

        public LockSingleProjectBuilder(Builder delegate, ProjectInternal project, ProjectStateRegistry projectStateRegistry) {
            super(delegate);
            this.project = project;
            this.projectStateRegistry = projectStateRegistry;
        }

        @Override
        public Object build(Object parameter) {
            return projectStateRegistry.stateFor(project).fromMutableState(p -> delegate.build(parameter));
        }
    }

    private static class LockAllProjectsBuilder extends DelegatingBuilder {
        private final ProjectStateRegistry projectStateRegistry;

        public LockAllProjectsBuilder(Builder delegate, ProjectStateRegistry projectStateRegistry) {
            super(delegate);
            this.projectStateRegistry = projectStateRegistry;
        }

        @Override
        public Object build(Object parameter) {
            return projectStateRegistry.withMutableStateOfAllProjects(() -> delegate.build(parameter));
        }
    }

    private static class BuildOperationWrappingBuilder extends DelegatingBuilder {
        private final String modelName;
        private final ProjectInternal project;
        private final BuildOperationExecutor buildOperationExecutor;

        private BuildOperationWrappingBuilder(Builder delegate, String modelName, ProjectInternal project, BuildOperationExecutor buildOperationExecutor) {
            super(delegate);
            this.modelName = modelName;
            this.project = project;
            this.buildOperationExecutor = buildOperationExecutor;
        }

        @Override
        public Object build(Object parameter) {
            return buildOperationExecutor.call(new CallableBuildOperation<Object>() {
                @Override
                public Object call(BuildOperationContext context) {
                    return delegate.build(parameter);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Build model '" + modelName + "' for " + project.getDisplayName()).
                        progressDisplayName("Building model '" + modelName + "'");
                }
            });
        }
    }

    private static class VoidToolingModelBuilder implements ToolingModelBuilder {
        @Override
        public boolean canBuild(String modelName) {
            return modelName.equals(Void.class.getName());
        }

        @Override
        public Object buildAll(String modelName, Project project) {
            return null;
        }
    }
}
