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

package org.gradle.launcher.exec;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

public class DaemonUsageSuggestingBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    public static final String PLEASE_USE_DAEMON_MESSAGE_PREFIX = "This build could be faster, please consider using the Gradle Daemon: ";

    private final BuildActionExecuter<BuildActionParameters> executer;
    private final StyledTextOutputFactory textOutputFactory;
    private final DocumentationRegistry documentationRegistry;
    private final OperatingSystem operatingSystem;

    public DaemonUsageSuggestingBuildActionExecuter(BuildActionExecuter<BuildActionParameters> executer, StyledTextOutputFactory textOutputFactory,
                                                    DocumentationRegistry documentationRegistry) {
        this(executer, textOutputFactory, documentationRegistry, OperatingSystem.current());
    }

    DaemonUsageSuggestingBuildActionExecuter(BuildActionExecuter<BuildActionParameters> executer, StyledTextOutputFactory textOutputFactory,
                                                    DocumentationRegistry documentationRegistry, OperatingSystem operatingSystem) {
        this.executer = executer;
        this.textOutputFactory = textOutputFactory;
        this.documentationRegistry = documentationRegistry;
        this.operatingSystem = operatingSystem;
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        Object result = executer.execute(action, requestContext, actionParameters, contextServices);
        possiblySuggestUsingDaemon(actionParameters);
        return result;
    }

    private void possiblySuggestUsingDaemon(BuildActionParameters actionParameters) {
        if (actionParameters.getDaemonUsage().isExplicitlySet()
                || operatingSystem.isWindows()
                || isCIEnvironment(actionParameters)) {
            return;
        }
        StyledTextOutput styledTextOutput = textOutputFactory.create(DaemonUsageSuggestingBuildActionExecuter.class, LogLevel.LIFECYCLE);
        styledTextOutput.println();
        styledTextOutput.println(PLEASE_USE_DAEMON_MESSAGE_PREFIX + documentationRegistry.getDocumentationFor("gradle_daemon"));
    }

    private boolean isCIEnvironment(BuildActionParameters actionParameters) {
        return actionParameters.getEnvVariables().get("CI") != null && !actionParameters.getEnvVariables().get("CI").equals("false");
    }
}
