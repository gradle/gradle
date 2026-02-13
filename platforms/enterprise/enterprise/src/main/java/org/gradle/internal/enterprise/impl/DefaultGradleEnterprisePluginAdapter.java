/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.impl;

import org.gradle.api.problems.internal.BuildOperationProblem;
import org.gradle.internal.enterprise.GradleEnterprisePluginBuildState;
import org.gradle.internal.enterprise.GradleEnterprisePluginConfig;
import org.gradle.internal.enterprise.GradleEnterprisePluginEndOfBuildListener;
import org.gradle.internal.enterprise.GradleEnterprisePluginEndOfBuildListener.BuildFailure;
import org.gradle.internal.enterprise.GradleEnterprisePluginRequiredServices;
import org.gradle.internal.enterprise.GradleEnterprisePluginService;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceFactory;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceRef;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.enterprise.exceptions.ExceptionMetadataHelper;
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar;
import org.gradle.operations.problems.Failure;
import org.gradle.operations.problems.Problem;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Captures the state to recreate the {@link GradleEnterprisePluginService} instance.
 * <p>
 * The adapter is created on check-in in {@link DefaultGradleEnterprisePluginCheckInService} via {@link DefaultGradleEnterprisePluginAdapterFactory}.
 * Then the adapter is stored on the {@link GradleEnterprisePluginManager}.
 * <p>
 * There is some custom logic to store the adapter from the manager in the configuration cache and restore it afterward.
 * The pluginServices need to be recreated when loading from the configuration cache.
 * <p>
 * This must not be a service, since the configuration cache will not serialize services with state to the configuration cache.
 * Instead, it would re-use the newly registered services in the new build that causes the loss of pluginServiceFactory.
 */
public class DefaultGradleEnterprisePluginAdapter implements GradleEnterprisePluginAdapter {

    private final GradleEnterprisePluginServiceFactory pluginServiceFactory;
    private final GradleEnterprisePluginConfig config;
    private final GradleEnterprisePluginRequiredServices requiredServices;
    private final GradleEnterprisePluginBuildState buildState;
    private final GradleEnterprisePluginBackgroundJobExecutorsInternal backgroundJobExecutors;
    private final GradleEnterprisePluginServiceRefInternal pluginServiceRef;

    private final BuildOperationNotificationListenerRegistrar buildOperationNotificationListenerRegistrar;

    @Nullable
    private transient GradleEnterprisePluginService pluginService;

    public DefaultGradleEnterprisePluginAdapter(
        GradleEnterprisePluginServiceFactory pluginServiceFactory,
        GradleEnterprisePluginConfig config,
        GradleEnterprisePluginRequiredServices requiredServices,
        GradleEnterprisePluginBuildState buildState,
        GradleEnterprisePluginBackgroundJobExecutorsInternal backgroundJobExecutors,
        GradleEnterprisePluginServiceRefInternal pluginServiceRef,
        BuildOperationNotificationListenerRegistrar buildOperationNotificationListenerRegistrar
    ) {
        this.pluginServiceFactory = pluginServiceFactory;
        this.config = config;
        this.requiredServices = requiredServices;
        this.buildState = buildState;
        this.backgroundJobExecutors = backgroundJobExecutors;
        this.pluginServiceRef = pluginServiceRef;
        this.buildOperationNotificationListenerRegistrar = buildOperationNotificationListenerRegistrar;

        createPluginService();
    }

    public GradleEnterprisePluginServiceRef getPluginServiceRef() {
        return pluginServiceRef;
    }

    @Override
    public boolean shouldSaveToConfigurationCache() {
        return true;
    }

    @Override
    public void onLoadFromConfigurationCache() {
        createPluginService();
    }

    @Override
    public void buildFinished(@Nullable Throwable buildFailure, List<org.gradle.internal.problems.failure.Failure> buildFailures) {
        // Ensure that all tasks are complete prior to the buildFinished callback.
        backgroundJobExecutors.shutdown();

        if (pluginService != null) {
            pluginService.getEndOfBuildListener().buildFinished(new DefaultDevelocityPluginResult(buildFailure, buildFailures));
        }
    }

    private void createPluginService() {
        pluginService = pluginServiceFactory.create(config, requiredServices, buildState);
        pluginServiceRef.set(pluginService);
        buildOperationNotificationListenerRegistrar.register(pluginService.getBuildOperationNotificationListener());
    }

    private static class DefaultDevelocityPluginResult implements GradleEnterprisePluginEndOfBuildListener.BuildResult {
        @Nullable
        private final Throwable buildFailure;
        @Nullable
        private final List<org.gradle.internal.problems.failure.Failure> buildFailures;

        public DefaultDevelocityPluginResult(@Nullable Throwable buildFailure, @Nullable List<org.gradle.internal.problems.failure.Failure> buildFailures) {
            // Validate the invariant, but avoid failing in production to allow Develocity to receive _a_ result
            // to provide a better user experience in the face of a bug on the Gradle side
            assert (buildFailure == null && buildFailures == null) ||
                (buildFailure != null && buildFailures != null && !buildFailures.isEmpty());
            this.buildFailure = buildFailure;
            this.buildFailures = buildFailures;
        }

        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        @Nullable
        @Override
        public Throwable getFailure() {
            return buildFailure;
        }

        @Nullable
        @Override
        public BuildFailure getBuildFailure() {
            return buildFailures == null
                ? null
                : this::getBuildFailures;
        }

        private List<Failure> getBuildFailures() {
            return Objects.requireNonNull(buildFailures).stream()
                .map(DevelocityBuildFailure::new)
                .collect(Collectors.toList());
        }

    }

    private static class DevelocityBuildFailure implements Failure {

        private final org.gradle.internal.problems.failure.Failure failure;

        public DevelocityBuildFailure(org.gradle.internal.problems.failure.Failure failure) {
            this.failure = failure;
        }

        @Override
        public String getExceptionType() {
            return failure.getExceptionType().getName();
        }

        @Nullable
        @Override
        public String getMessage() {
            return failure.getHeader();
        }

        @Override
        public Map<String, String> getMetadata() {
            return ExceptionMetadataHelper.getMetadata(failure.getOriginal());
        }

        @Override
        public List<StackTraceElement> getStackTrace() {
            return failure.getStackTrace();
        }

        @Override
        public List<String> getClassLevelAnnotations() {
            return getClassLevelAnnotations(failure.getExceptionType());
        }

        @Override
        public List<Failure> getCauses() {
            return failure.getCauses().stream()
                .map(DevelocityBuildFailure::new)
                .collect(Collectors.toList());
        }

        @Override
        public List<Problem> getProblems() {
            return failure.getProblems().stream()
                .map(BuildOperationProblem::new)
                .collect(Collectors.toList());
        }

        private static List<String> getClassLevelAnnotations(Class<?> cls) {
            Set<String> anns = new HashSet<>();
            for (Annotation a : cls.getAnnotations()) {
                anns.add(a.annotationType().getName());
            }
            return new ArrayList<>(anns);
        }
    }

}
