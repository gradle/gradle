/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.project.GlobalServicesRegistry;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;
import org.gradle.logging.LoggingServiceRegistry;

/**
 * by Szczepan Faber, created at: 12/6/11
 */
public class EmbeddedExecuterSupport {

    private GradleLauncherFactory gradleLauncherFactory;
    private LoggingServiceRegistry embeddedLogging;

    public EmbeddedExecuterSupport() {
        embeddedLogging = LoggingServiceRegistry.newEmbeddableLogging();
        gradleLauncherFactory = new GlobalServicesRegistry(embeddedLogging).get(GradleLauncherFactory.class);
    }

    public BuildActionExecuter<BuildActionParameters> getExecuter() {
        return new InProcessBuildActionExecuter(gradleLauncherFactory);
    }

    public LoggingServiceRegistry getLoggingServices() {
        return embeddedLogging;
    }
}