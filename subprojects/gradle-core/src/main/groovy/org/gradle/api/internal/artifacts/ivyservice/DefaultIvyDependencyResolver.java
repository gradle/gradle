/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.util.Message;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.*;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.logging.IvyLoggingAdaper;
import org.gradle.util.Clock;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Formatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultIvyDependencyResolver implements IvyDependencyResolver {
    private static Logger logger = LoggerFactory.getLogger(DefaultIvyDependencyResolver.class);

    private IvyReportConverter ivyReportTranslator;

    public DefaultIvyDependencyResolver(IvyReportConverter ivyReportTranslator) {
        this.ivyReportTranslator = ivyReportTranslator;
        Message.setDefaultLogger(new IvyLoggingAdaper());
    }

    public ResolvedConfiguration resolve(Configuration configuration, Ivy ivy, ModuleDescriptor moduleDescriptor) {
        Clock clock = new Clock();
        ResolveOptions resolveOptions = createResolveOptions(configuration);
        ResolveReport resolveReport;
        try {
            resolveReport = ivy.resolve(moduleDescriptor, resolveOptions);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.debug("Timing: Ivy resolve took {}", clock.getTime());
        return new ResolvedConfigurationImpl(resolveReport, configuration);
    }

    private ResolveOptions createResolveOptions(Configuration configuration) {
        ResolveOptions resolveOptions = new ResolveOptions();
        resolveOptions.setDownload(false);
        resolveOptions.setConfs(WrapUtil.toArray(configuration.getName()));
        return resolveOptions;
    }

    class ResolvedConfigurationImpl implements ResolvedConfiguration {
        private final ResolveReport resolveReport;
        private final Configuration configuration;
        private IvyConversionResult conversionResult;

        public ResolvedConfigurationImpl(ResolveReport resolveReport, Configuration configuration) {
            this.resolveReport = resolveReport;
            this.configuration = configuration;
        }

        public boolean hasError() {
            return resolveReport.hasError();
        }

        public void rethrowFailure() throws ResolveException {
            if (resolveReport.hasError()) {
                Formatter formatter = new Formatter();
                for (Object msg : resolveReport.getAllProblemMessages()) {
                    formatter.format("    - %s%n", msg);
                }
                throw new ResolveException(configuration, formatter.toString());
            }
        }

        public Set<File> getFiles(Spec<Dependency> dependencySpec) {
            buildResolvedDependencies();
            Set<ModuleDependency> allModuleDependencies = Specs.filterIterable(configuration.getAllDependencies(ModuleDependency.class), dependencySpec);
            Set<File> files = new LinkedHashSet<File>();
            for (ModuleDependency moduleDependency : allModuleDependencies) {
                Set<ResolvedDependency> resolvedDependencies = conversionResult.getFirstLevelResolvedDependencies().get(moduleDependency);
                if (resolvedDependencies != null) {
                    for (ResolvedDependency resolvedDependency : resolvedDependencies) {
                        for (File depFile : ResolvedDependencies.getFilesFromArtifacts(resolvedDependency.getAllArtifacts(null))) {
                            if (depFile == null) {
                                throw new GradleException(String.format("Resolved files for %s contains a null value.", resolvedDependency));
                            }
                            files.add(depFile);
                        }
                    }
                }
            }
            return files;
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
            buildResolvedDependencies();
            Set<ResolvedDependency> resolvedDependencies = new LinkedHashSet<ResolvedDependency>();
            for (Dependency dependency : conversionResult.getFirstLevelResolvedDependencies().keySet()) {
                Set<ResolvedDependency> dependencySet = conversionResult.getFirstLevelResolvedDependencies().get(dependency);
                if (dependencySet != null) {
                    resolvedDependencies.addAll(dependencySet);
                }
            }
            return resolvedDependencies;
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() {
            buildResolvedDependencies();
            return conversionResult.getResolvedArtifacts();
        }

        private void buildResolvedDependencies() {
            rethrowFailure();
            if (conversionResult != null) {
                return;
            }
            conversionResult = ivyReportTranslator.convertReport(
                    resolveReport,
                    configuration);
        }
    }
}
