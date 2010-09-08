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
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.CachingDirectedGraphWalker;
import org.gradle.api.internal.DirectedGraphWithEdgeValues;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.Clock;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

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
        private final Configuration configuration;
        private boolean hasError;
        private List<String> problemMessages;
        private IvyConversionResult conversionResult;
        private final CachingDirectedGraphWalker<ResolvedDependency, ResolvedArtifact> walker
                = new CachingDirectedGraphWalker<ResolvedDependency, ResolvedArtifact>(new ResolvedDependencyArtifactsGraph());

        public ResolvedConfigurationImpl(ResolveReport resolveReport, Configuration configuration) {
            this.hasError = resolveReport.hasError();
            if (this.hasError) {
                this.problemMessages = resolveReport.getAllProblemMessages();
            } else {
                 this.conversionResult = ivyReportTranslator.convertReport(
                    resolveReport,
                    configuration);
            }
            this.configuration = configuration;
        }

        public boolean hasError() {
            return hasError;
        }

        public void rethrowFailure() throws ResolveException {
            if (hasError) {
                Formatter formatter = new Formatter();
                for (String msg : problemMessages) {
                    formatter.format("    - %s%n", msg);
                }
                throw new ResolveException(configuration, formatter.toString());
            }
        }

        public Set<File> getFiles(Spec<Dependency> dependencySpec) {
            rethrowFailure();
            Set<ModuleDependency> allDependencies = configuration.getAllDependencies(ModuleDependency.class);
            Set<ModuleDependency> selectedDependencies = Specs.filterIterable(allDependencies, dependencySpec);

            Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();

            for (ModuleDependency moduleDependency : selectedDependencies) {
                Set<ResolvedDependency> resolvedDependencies = conversionResult.getFirstLevelResolvedDependencies().get(moduleDependency);
                for (ResolvedDependency resolvedDependency : resolvedDependencies) {
                    artifacts.addAll(resolvedDependency.getParentArtifacts(conversionResult.getRoot()));
                    walker.add(resolvedDependency);
                }
            }

            artifacts.addAll(walker.findValues());

            Set<File> files = new LinkedHashSet<File>();
            for (ResolvedArtifact artifact : artifacts) {
                File depFile = artifact.getFile();
                if (depFile != null) {
                    files.add(depFile);
                } else {
                    logger.debug(String.format("Resolved artifact %s contains a null value.", artifact));
                }
            }
            return files;
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
            rethrowFailure();
            return conversionResult.getRoot().getChildren();
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() {
            rethrowFailure();
            return conversionResult.getResolvedArtifacts();
        }

        private class ResolvedDependencyArtifactsGraph implements DirectedGraphWithEdgeValues<ResolvedDependency, ResolvedArtifact> {
            public void getNodeValues(ResolvedDependency node, Collection<ResolvedArtifact> values,
                                      Collection<ResolvedDependency> connectedNodes) {
                values.addAll(node.getModuleArtifacts());
                connectedNodes.addAll(node.getChildren());
            }

            public void getEdgeValues(ResolvedDependency from, ResolvedDependency to,
                                      Collection<ResolvedArtifact> values) {
                values.addAll(to.getParentArtifacts(from));
            }
        }
    }
}
