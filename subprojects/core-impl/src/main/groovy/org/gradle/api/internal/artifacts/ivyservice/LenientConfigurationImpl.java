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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.gradle.api.artifacts.*;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author: Szczepan Faber, created at: 8/9/11
 */
public class LenientConfigurationImpl implements LenientConfiguration {
    private final DefaultIvyDependencyResolver.ResolvedConfigurationImpl delegate;
    private final ResolveReport resolveReport;
    private final Configuration configuration;

    public LenientConfigurationImpl(DefaultIvyDependencyResolver.ResolvedConfigurationImpl resolvedConfiguration, ResolveReport resolveReport, Configuration configuration) {
        this.delegate = resolvedConfiguration;
        this.resolveReport = resolveReport;
        this.configuration = configuration;
    }

    public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<Dependency> dependencySpec) {
        return delegate.doGetFirstLevelModuleDependencies(dependencySpec);
    }

    public Set<File> getFiles(Spec<Dependency> dependencySpec) {
        return delegate.doGetFiles(dependencySpec);
    }

    public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
        Set<UnresolvedDependency> result = new LinkedHashSet<UnresolvedDependency>();
        IvyNode[] unresolved = resolveReport.getConfigurationReport(configuration.getName()).getUnresolvedDependencies();

        for (IvyNode node : unresolved) {
            result.add(new UnResolvedDependencyImpl(node.getId().toString(), configuration, node.getProblem()));
        }

        return result;
    }

    private class UnResolvedDependencyImpl implements UnresolvedDependency {

        private final String id;
        private final Configuration configuration;
        private final Exception problem;

        public UnResolvedDependencyImpl(String id, Configuration configuration, Exception problem) {
            this.id = id;
            this.configuration = configuration;
            this.problem = problem;
        }

        public Configuration getGradleConfiguration() {
            return configuration;
        }

        public String getId() {
            return id;
        }

        public Exception getProblem() {
            return problem;
        }

        public String toString() {
            return id;
        }
    }
}
