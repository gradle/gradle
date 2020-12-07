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

package org.gradle.internal.buildtree;

import org.gradle.api.internal.BuildType;
import org.gradle.api.internal.project.DefaultProjectStateRegistry;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.initialization.exception.DefaultExceptionAnalyser;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.exception.MultipleBuildFailuresExceptionAnalyser;
import org.gradle.initialization.exception.StackTraceSanitizingExceptionAnalyser;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.steps.ValidateStep;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.work.WorkerLeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Contains the singleton services for a single build tree which consists of one or more builds.
 */
public class BuildTreeScopeServices {
    private final BuildTreeState buildTree;
    private final BuildType buildType;

    public BuildTreeScopeServices(BuildTreeState buildTree, BuildType buildType) {
        this.buildTree = buildTree;
        this.buildType = buildType;
    }

    protected void configure(ServiceRegistration registration, List<PluginServiceRegistry> pluginServiceRegistries) {
        for (PluginServiceRegistry pluginServiceRegistry : pluginServiceRegistries) {
            pluginServiceRegistry.registerBuildTreeServices(registration);
        }
        registration.add(BuildTreeState.class, buildTree);
        registration.add(BuildType.class, buildType);
        registration.add(GradleEnterprisePluginManager.class);
    }

    protected ListenerManager createListenerManager(ListenerManager parent) {
        return parent.createChild(Scopes.BuildTree.class);
    }

    protected ExceptionAnalyser createExceptionAnalyser(ListenerManager listenerManager, LoggingConfiguration loggingConfiguration) {
        ExceptionAnalyser exceptionAnalyser = new MultipleBuildFailuresExceptionAnalyser(new DefaultExceptionAnalyser(listenerManager));
        if (loggingConfiguration.getShowStacktrace() != ShowStacktrace.ALWAYS_FULL) {
            exceptionAnalyser = new StackTraceSanitizingExceptionAnalyser(exceptionAnalyser);
        }
        return exceptionAnalyser;
    }

    public DefaultProjectStateRegistry createProjectPathRegistry(WorkerLeaseService workerLeaseService) {
        return new DefaultProjectStateRegistry(workerLeaseService);
    }

    ValidateStep.ValidationWarningReporter createValidationWarningReporter() {
        return new DefaultWorkValidationWarningReporter();
    }

    private static class DefaultWorkValidationWarningReporter implements ValidateStep.ValidationWarningReporter, Closeable {
        private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkValidationWarningReporter.class);

        private final AtomicInteger workWithFailuresCount = new AtomicInteger();

        @Override
        public void reportValidationWarnings(UnitOfWork work, Collection<String> warnings) {
            workWithFailuresCount.incrementAndGet();
            LOGGER.warn("Validation failed for {}, disabling optimizations:{}",
                work.getDisplayName(),
                warnings.stream().map(warning -> "\n  - " + warning).collect(Collectors.joining()));
            warnings.forEach(warning -> DeprecationLogger.deprecateBehaviour(warning)
                .withContext("Due to the failed validation execution optimizations are disabled.")
                .willBeRemovedInGradle7()
                .withUserManual("more_about_tasks", "sec:up_to_date_checks")
                .nagUser());
        }

        @Override
        public void close() {
            int workWithFailures = workWithFailuresCount.get();
            if (workWithFailures > 0) {
                LOGGER.warn("Execution optimizations have been disabled for {} invalid unit(s) of work during the build. Consult deprecation warnings for more information.", workWithFailures);
            }
        }
    }
}
