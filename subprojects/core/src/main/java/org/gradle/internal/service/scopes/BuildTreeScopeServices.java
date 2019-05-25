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

package org.gradle.internal.service.scopes;

import org.gradle.api.Action;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.api.internal.project.DefaultProjectStateRegistry;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.initialization.exception.DefaultExceptionAnalyser;
import org.gradle.initialization.exception.MultipleBuildFailuresExceptionAnalyser;
import org.gradle.initialization.exception.StackTraceSanitizingExceptionAnalyser;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.WorkerLeaseService;

/**
 * Contains the singleton services for a single build tree which consists of one or more builds.
 */
public class BuildTreeScopeServices extends DefaultServiceRegistry {
    public BuildTreeScopeServices(final ServiceRegistry parent) {
        super(parent);
        register(new Action<ServiceRegistration>() {
            @Override
            public void execute(ServiceRegistration registration) {
                for (PluginServiceRegistry pluginServiceRegistry : parent.getAll(PluginServiceRegistry.class)) {
                    pluginServiceRegistry.registerBuildTreeServices(registration);
                }
            }
        });
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
}
