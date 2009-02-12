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
import org.gradle.api.artifacts.ResolveInstruction;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultIvyDependencyResolver implements IvyDependencyResolver {
    private static Logger logger = LoggerFactory.getLogger(DefaultIvyDependencyResolver.class);

    private ResolveOptionsFactory resolveOptionsFactory;
    private Report2Classpath report2Classpath;

    private ResolveReport lastResolveReport = null;

    public DefaultIvyDependencyResolver(ResolveOptionsFactory resolveOptionsFactory, Report2Classpath report2Classpath) {
        this.resolveOptionsFactory = resolveOptionsFactory;
        this.report2Classpath = report2Classpath;
    }

    public List<File> resolve(String conf, ResolveInstruction resolveInstruction, Ivy ivy, ModuleDescriptor moduleDescriptor) {
        ResolveReport resolveReport = resolveAsReport(conf, resolveInstruction, ivy, moduleDescriptor);
        return report2Classpath.getClasspath(conf, resolveReport);
    }

    public List<File> resolveFromReport(String conf, ResolveReport resolveReport) {
       return report2Classpath.getClasspath(conf, resolveReport);
    }

    public ResolveReport resolveAsReport(String conf, ResolveInstruction resolveInstruction, Ivy ivy, ModuleDescriptor moduleDescriptor) {
        Clock clock = new Clock();
        ResolveOptions resolveOptions = resolveOptionsFactory.createResolveOptions(conf, resolveInstruction);
        ResolveReport resolveReport = null;
        try {
            resolveReport = ivy.resolve(
                    moduleDescriptor, resolveOptions);
            lastResolveReport = resolveReport;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.debug("Timing: Ivy resolve took {}", clock.getTime());
        if (resolveReport.hasError() && resolveInstruction.isFailOnResolveError()) {
            throw new GradleException("Not all dependencies could be resolved!");
        }
        logger.debug("Timing: Complete resolve took {}", clock.getTime());
        return resolveReport;
    }

    public ResolveReport getLastResolveReport() {
        return lastResolveReport;
    }
}
