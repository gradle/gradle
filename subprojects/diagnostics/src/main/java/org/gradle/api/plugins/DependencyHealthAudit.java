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

package org.gradle.api.plugins;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.reporting.dependencies.internal.DependencyHealthAnalyzer;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public abstract class DependencyHealthAudit extends DefaultTask {
    @Inject
    public abstract DependencyHealthAnalyzer getDependencyHealthAnalyzer();

    @TaskAction
    public void analyze() {
        Logger logger = getLogger();
        for (Configuration configuration : getProject().getConfigurations()) {
            if (configuration.isCanBeResolved()) {
                logger.lifecycle("Configuration " + configuration.getName());
                for (ResolvedDependency dependency : configuration.getResolvedConfiguration().getLenientConfiguration().getAllModuleDependencies()) {
                    DependencyHealthAnalyzer.HealthReport report = getDependencyHealthAnalyzer().analyze(dependency.getModuleGroup(), dependency.getModuleName(), dependency.getModuleVersion());
                    if (!report.getCves().isEmpty()) {
                        logger.lifecycle("\t" + dependency.getModule().getId());
                        for (DependencyHealthAnalyzer.Cve cve : report.getCves()) {
                            logger.lifecycle("\t\t" + cve.getId() + " CVSSv3 " + cve.getScore());
                        }
                    }
                }
            }
        }
    }
}
