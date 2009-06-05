/*
 * Copyright 2007-2009 the original author or authors.
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
import org.gradle.api.GradleException;
import org.gradle.api.internal.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.Configuration;
import org.gradle.util.Clock;
import org.gradle.util.WrapUtil;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;
import java.util.Formatter;

/**
 * @author Hans Dockter
 */
public class DefaultIvyDependencyResolver implements IvyDependencyResolver {
    private static Logger logger = LoggerFactory.getLogger(DefaultIvyDependencyResolver.class);

    private Report2Classpath report2Classpath;

    public DefaultIvyDependencyResolver(Report2Classpath report2Classpath) {
        this.report2Classpath = report2Classpath;
    }

    public ResolvedConfiguration resolve(final Configuration configuration, Ivy ivy, ModuleDescriptor moduleDescriptor) {
        final ResolveReport resolveReport = resolveAsReport(configuration, ivy, moduleDescriptor);
        return new ResolvedConfiguration() {
            public ResolveReport getResolveReport() {
                return resolveReport;
            }

            public boolean hasError() {
                return resolveReport.hasError();
            }

            public void rethrowFailure() throws GradleException {
                if (resolveReport.hasError()) {
                    Formatter formatter = new Formatter();
                    formatter.format("Could not resolve all dependencies for %s:%n", configuration);
                    for (Object msg : resolveReport.getAllProblemMessages()) {
                        formatter.format("    - %s%n", msg);
                    }
                    throw new GradleException(formatter.toString());
                }
            }

            public Set<File> getFiles() {
                rethrowFailure();
                return report2Classpath.getClasspath(configuration.getName(), resolveReport);
            }
        };
    }

    private ResolveReport resolveAsReport(Configuration configuration, Ivy ivy, ModuleDescriptor moduleDescriptor) {
        Clock clock = new Clock();
        ResolveOptions resolveOptions = createResolveOptions(configuration);
        ResolveReport resolveReport;
        try {
            resolveReport = ivy.resolve(moduleDescriptor, resolveOptions);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.debug("Timing: Ivy resolve took {}", clock.getTime());
        return resolveReport;
    }

    private ResolveOptions createResolveOptions(Configuration configuration) {
        ResolveOptions resolveOptions = new ResolveOptions();
        resolveOptions.setConfs(WrapUtil.toArray(configuration.getName()));
        return resolveOptions;
    }
}
