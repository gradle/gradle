/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.gradle.api.GradleException;
import org.gradle.util.Clock;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyResolver implements IDependencyResolver {
    private static Logger logger = LoggerFactory.getLogger(DefaultDependencyResolver.class);

    Map<String, List<File>> resolveCache = new HashMap<String, List<File>>();

    Report2Classpath report2Classpath;

    public DefaultDependencyResolver(Report2Classpath report2Classpath) {
        this.report2Classpath = report2Classpath;
    }

    public List<File> resolve(String conf, Ivy ivy, ModuleDescriptor moduleDescriptor, boolean failForMissingDependencies) {
        Clock clock = new Clock();
        if (resolveCache.keySet().contains(conf)) {
            return resolveCache.get(conf);
        }
        //        ivy.configure(new File('/Users/hans/IdeaProjects/gradle/gradle-core/ivysettings.xml'))
        ResolveOptions resolveOptions = new ResolveOptions();
        resolveOptions.setConfs(WrapUtil.toArray(conf));
        resolveOptions.setOutputReport(true);
        Clock ivyClock = new Clock();
        ResolveReport resolveReport = null;
        try {
            resolveReport = ivy.resolve(moduleDescriptor, resolveOptions);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.debug("Timing: Ivy resolve took {}", clock.getTime());
        if (resolveReport.hasError() && failForMissingDependencies) {
            throw new GradleException("Not all dependencies could be resolved!");
        }
        resolveCache.put(conf, report2Classpath.getClasspath(conf, resolveReport));
        logger.debug("Timing: Complete resolve took {}", clock.getTime());
        return resolveCache.get(conf);
    }
}
