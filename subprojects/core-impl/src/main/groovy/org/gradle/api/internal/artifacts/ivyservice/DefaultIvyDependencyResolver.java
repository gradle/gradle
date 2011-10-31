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
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultIvyDependencyResolver implements ArtifactDependencyResolver {
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final ResolveIvyFactory ivyFactory;
    private final IvyReportConverter ivyReportTranslator;

    public DefaultIvyDependencyResolver(IvyReportConverter ivyReportTranslator, ModuleDescriptorConverter moduleDescriptorConverter, ResolveIvyFactory ivyFactory) {
        this.ivyReportTranslator = ivyReportTranslator;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.ivyFactory = ivyFactory;
    }

    public ResolvedConfiguration resolve(ConfigurationInternal configuration) {
        Ivy ivy = ivyFactory.create(configuration.getResolutionStrategy());

        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(configuration.getAll(), configuration.getModule());
        ResolveOptions resolveOptions = createResolveOptions(configuration);
        ResolveReport resolveReport;
        try {
            resolveReport = ivy.resolve(moduleDescriptor, resolveOptions);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new ResolvedConfigurationImpl(resolveReport, configuration);
    }

    private ResolveOptions createResolveOptions(Configuration configuration) {
        ResolveOptions resolveOptions = new ResolveOptions();
        resolveOptions.setDownload(false);
        resolveOptions.setConfs(WrapUtil.toArray(configuration.getName()));
        return resolveOptions;
    }

    class ResolvedConfigurationImpl extends AbstractResolvedConfiguration {
        private final Configuration configuration;
        private boolean hasError;
        private List<String> problemMessages;
        private IvyConversionResult conversionResult;
        private final ResolveReport resolveReport;

        public ResolvedConfigurationImpl(ResolveReport resolveReport, Configuration configuration) {
            this.resolveReport = resolveReport;
            this.hasError = resolveReport.hasError();
            if (this.hasError) {
                this.problemMessages = resolveReport.getAllProblemMessages();
            }
            conversionResult = ivyReportTranslator.convertReport(resolveReport, configuration);
            this.configuration = configuration;
        }

        @Override
        protected ResolvedDependency getRoot() {
            return conversionResult.getRoot();
        }

        public boolean hasError() {
            return hasError;
        }

        public void rethrowFailure() throws ResolveException {
            if (hasError) {
                // Note: this list does not include all the failures, but it's better than nothing
                List<Throwable> unresolvedFailures = new ArrayList<Throwable>();
                IvyNode[] unresolved = resolveReport.getConfigurationReport(configuration.getName()).getUnresolvedDependencies();
                for (IvyNode node : unresolved) {
                    unresolvedFailures.add(node.getProblem());
                }

                throw new ResolveException(configuration, problemMessages, unresolvedFailures);
            }
        }

        @Override
        Set<UnresolvedDependency> getUnresolvedDependencies() {
            Set<UnresolvedDependency> result = new LinkedHashSet<UnresolvedDependency>();
            IvyNode[] unresolved = resolveReport.getConfigurationReport(configuration.getName()).getUnresolvedDependencies();

            for (IvyNode node : unresolved) {
                result.add(new DefaultUnresolvedDependency(node.getId().toString(), configuration, node.getProblem()));
            }

            return result;
        }

        @Override
        Set<ResolvedDependency> doGetFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
            Set<ModuleDependency> allDependencies = configuration.getAllDependencies().withType(ModuleDependency.class);
            Set<ModuleDependency> selectedDependencies = Specs.filterIterable(allDependencies, dependencySpec);

            Set<ResolvedDependency> result = new LinkedHashSet<ResolvedDependency>();
            for (ModuleDependency moduleDependency : selectedDependencies) {
                result.addAll(conversionResult.getFirstLevelResolvedDependencies().get(moduleDependency));
            }

            return result;
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() {
            rethrowFailure();
            return conversionResult.getResolvedArtifacts();
        }
    }
}
