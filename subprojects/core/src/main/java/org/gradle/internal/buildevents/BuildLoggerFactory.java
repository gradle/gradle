/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.buildevents;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.execution.WorkValidationWarningReporter;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.Clock;

@ServiceScope({Scope.Global.class, Scope.BuildTree.class})
public class BuildLoggerFactory {
    private final StyledTextOutputFactory styledTextOutputFactory;
    private final WorkValidationWarningReporter workValidationWarningReporter;
    private final Clock clock;
    private final GradleEnterprisePluginManager gradleEnterprisePluginManager;

    public BuildLoggerFactory(StyledTextOutputFactory styledTextOutputFactory, WorkValidationWarningReporter workValidationWarningReporter, Clock clock, GradleEnterprisePluginManager gradleEnterprisePluginManager) {
        this.styledTextOutputFactory = styledTextOutputFactory;
        this.workValidationWarningReporter = workValidationWarningReporter;
        this.clock = clock;
        this.gradleEnterprisePluginManager = gradleEnterprisePluginManager;
    }

    public BuildLogger create(Logger logger, LoggingConfiguration loggingConfiguration, BuildStartedTime buildStartedTime, BuildRequestMetaData buildRequestMetaData) {
        return new BuildLogger(logger, styledTextOutputFactory, loggingConfiguration, buildRequestMetaData, buildStartedTime, clock, workValidationWarningReporter, gradleEnterprisePluginManager);
    }
}
