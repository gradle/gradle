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

import com.google.common.collect.Iterators;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

import javax.annotation.Nullable;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class DefaultToolingModelBuilderRegistry implements ToolingModelBuilderRegistry, ToolingModelBuilderLookup {
    private final ToolingModelBuilderLookup parent;

    private final List<RegistrationImpl> registrations = new ArrayList<>();
    // This is a workaround for https://github.com/gradle/gradle/issues/17319. IDEA reads this field in an attempt to check if its Builder is already registered.
    @SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
    private final Collection<ToolingModelBuilder> builders = new AbstractCollection<ToolingModelBuilder>() {
        @Override
        public Iterator<ToolingModelBuilder> iterator() {
            return Iterators.transform(registrations.iterator(), RegistrationImpl::getBuilder);
        }

        @Override
        public int size() {
            return registrations.size();
        }
    };

    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectStateRegistry projectStateRegistry;
    private final UserCodeApplicationContext userCodeApplicationContext;

    public DefaultToolingModelBuilderRegistry(BuildOperationExecutor buildOperationExecutor, ProjectStateRegistry projectStateRegistry, UserCodeApplicationContext userCodeApplicationContext) {
        this(buildOperationExecutor, projectStateRegistry, null, userCodeApplicationContext);
        register(new VoidToolingModelBuilder());
    }

    private DefaultToolingModelBuilderRegistry(BuildOperationExecutor buildOperationExecutor, @Nullable ProjectStateRegistry projectStateRegistry, ToolingModelBuilderLookup parent, UserCodeApplicationContext userCodeApplicationContext) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.projectStateRegistry = projectStateRegistry;
        this.parent = parent;
        this.userCodeApplicationContext = userCodeApplicationContext;
    }

    public DefaultToolingModelBuilderRegistry createChild() {
        return new DefaultToolingModelBuilderRegistry(buildOperationExecutor, projectStateRegistry, this, userCodeApplicationContext);
    }

    @Override
    public void register(ToolingModelBuilder builder) {
        registrations.add(new RegistrationImpl(userCodeApplicationContext.current(), builder));
    }

    @Override
    public ToolingModelBuilder getBuilder(String modelName) throws UnsupportedOperationException {
        Registration registration = get(modelName);
        return new LenientToolingModelBuilder(registration.getBuilder(), projectStateRegistry);
    }

    @Override
    public Builder locateForClientOperation(String modelName, boolean parameter, ProjectState target) throws UnknownModelException {
        return new BuildOperationWrappingBuilder(
            new LockSingleProjectBuilder(
                locateForClientOperation(modelName, target.getMutableModel(), parameter), target),
            modelName, target.getOwner(), target, target.getDisplayName(), buildOperationExecutor);
    }

    @Nullable
    @Override
    public Builder maybeLocateForBuildScope(String modelName, boolean parameter, BuildState target) {
        Registration registration = find(modelName);
        if (registration == null) {
            return null;
        }

        if (!(registration.getBuilder() instanceof BuildScopeModelBuilder)) {
            return null;
        }

        BuildScopeModelBuilder buildScopeModelBuilder = (BuildScopeModelBuilder) registration.getBuilder();
        return new BuildOperationWrappingBuilder(
            restoreUserCodeApplication(
                new BuildScopedBuilder(buildScopeModelBuilder, target), registration),
            modelName, target, null, target.getDisplayName(), buildOperationExecutor);
    }

    private Builder locateForClientOperation(String modelName, ProjectInternal project, boolean parameter) throws UnknownModelException {
        Registration registration = get(modelName);
        if (registration.getBuilder() instanceof ParameterizedToolingModelBuilder) {
            return restoreUserCodeApplication(new BuilderWithParameter(modelName, project, (ParameterizedToolingModelBuilder) registration.getBuilder()), registration);
        }
        if (parameter) {
            throw new UnknownModelException(String.format("No parameterized builders are available to build a model of type '%s'.", modelName));
        }
        return restoreUserCodeApplication(new BuilderWithNoParameter(modelName, project, registration.getBuilder()), registration);
    }

    private Builder restoreUserCodeApplication(Builder delegate, Registration registration) {
        UserCodeApplicationContext.Application registeredBy = registration.getRegisteredBy();
        if (registeredBy == null) {
            return delegate;
        } else {
            return new UserCodeAssigningBuilder(delegate, registeredBy);
        }
    }

    @Nullable
    public Registration find(String modelName) {
        Registration match = null;
        for (RegistrationImpl registration : registrations) {
            if (registration.builder.canBuild(modelName)) {
                if (match != null) {
                    throw new UnsupportedOperationException(String.format("Multiple builders are available to build a model of type '%s'.", modelName));
                }
                match = registration;
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

    private Registration get(String modelName) {
        Registration registration = find(modelName);
        if (registration != null) {
            return registration;
        }

        throw new UnknownModelException(String.format("No builders are available to build a model of type '%s'.", modelName));
    }

    private static class RegistrationImpl implements Registration {
        final UserCodeApplicationContext.Application registeredBy;
        final ToolingModelBuilder builder;

        public RegistrationImpl(UserCodeApplicationContext.Application registeredBy, ToolingModelBuilder builder) {
            this.registeredBy = registeredBy;
            this.builder = builder;
        }

        @Override
        public ToolingModelBuilder getBuilder() {
            return builder;
        }

        @Override
        public UserCodeApplicationContext.Application getRegisteredBy() {
            return registeredBy;
        }
    }

    private static class BuildScopedBuilder implements Builder {
        private final BuildScopeModelBuilder buildScopeModelBuilder;
        private final BuildState target;

        public BuildScopedBuilder(BuildScopeModelBuilder buildScopeModelBuilder, BuildState target) {
            this.buildScopeModelBuilder = buildScopeModelBuilder;
            this.target = target;
        }

        @Nullable
        @Override
        public Class<?> getParameterType() {
            return null;
        }

        @Override
        public Object build(@Nullable Object parameter) {
            return buildScopeModelBuilder.create(target);
        }
    }

    private static class LenientToolingModelBuilder implements ToolingModelBuilder {
        private final ToolingModelBuilder delegate;
        private final ProjectStateRegistry projectStateRegistry;

        public LenientToolingModelBuilder(ToolingModelBuilder delegate, ProjectStateRegistry projectStateRegistry) {
            this.delegate = delegate;
            this.projectStateRegistry = projectStateRegistry;
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
        private final ProjectState target;

        public LockSingleProjectBuilder(Builder delegate, ProjectState target) {
            super(delegate);
            this.target = target;
        }

        @Override
        public Object build(Object parameter) {
            return target.fromMutableState(p -> delegate.build(parameter));
        }
    }

    private static class BuildOperationWrappingBuilder extends DelegatingBuilder {
        private final String modelName;
        private final BuildState targetBuild;
        private final ProjectState targetProject;
        private final DisplayName targetDisplayName;
        private final BuildOperationExecutor buildOperationExecutor;

        private BuildOperationWrappingBuilder(
            Builder delegate,
            String modelName,
            BuildState targetBuild,
            @Nullable
            ProjectState targetProject,
            DisplayName targetDisplayName,
            BuildOperationExecutor buildOperationExecutor
        ) {
            super(delegate);
            this.modelName = modelName;
            this.targetBuild = targetBuild;
            this.targetProject = targetProject;
            this.targetDisplayName = targetDisplayName;
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
                    return BuildOperationDescriptor.displayName("Build model '" + modelName + "' for " + targetDisplayName.getDisplayName()).
                        progressDisplayName("Building model '" + modelName + "'").details(new QueryToolingModelBuildOperationType.Details() {
                            @Override
                            public String getBuildPath() {
                                return targetBuild.getIdentityPath().getPath();
                            }

                            @Nullable
                            @Override
                            public String getProjectPath() {
                                if (targetProject != null) {
                                    return targetProject.getProjectPath().getPath();
                                } else {
                                    return null;
                                }
                            }
                        });
                }
            });
        }
    }

    private static class UserCodeAssigningBuilder extends DelegatingBuilder {
        private final UserCodeApplicationContext.Application registeredBy;

        public UserCodeAssigningBuilder(Builder delegate, UserCodeApplicationContext.Application registeredBy) {
            super(delegate);
            this.registeredBy = registeredBy;
        }

        @Override
        public Object build(@Nullable Object parameter) {
            return registeredBy.reapply(() -> delegate.build(parameter));
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
