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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.publish.PublishEngine;
import org.apache.ivy.core.publish.PublishOptions;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.dependencies.ResolverContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyPublisher implements IDependencyPublisher {
    private static Logger logger = LoggerFactory.getLogger(DefaultDependencyPublisher.class);

    public void publish(List<String> configurations,
                                   ResolverContainer resolvers,
                                   ModuleDescriptor moduleDescriptor,
                                   boolean uploadModuleDescriptor,
                                   File ivyFile,
                                   BaseDependencyManager dependencyManager,
                                   PublishEngine publishEngine) {
        PublishOptions publishOptions = new PublishOptions();
        if (uploadModuleDescriptor) {
            try {
                moduleDescriptor.toIvyFile(ivyFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            publishOptions.setSrcIvyPattern(ivyFile.getAbsolutePath());
        }
        publish(configurations, resolvers, moduleDescriptor,
                absoluteArtifactPatterns(dependencyManager.getAbsoluteArtifactPatterns(), dependencyManager.getDefaultArtifactPattern(), dependencyManager.getArtifactParentDirs()),
                publishOptions, publishEngine);
    }

    private void publish(List configurations, ResolverContainer resolvers, ModuleDescriptor moduleDescriptor,
                                    List<String> artifactPatterns, PublishOptions publishOptions, PublishEngine publishEngine) {
        publishOptions.setOverwrite(true);
        publishOptions.setConfs((String[]) configurations.toArray(new String[configurations.size()]));
        try {
            for (Object resolver : resolvers.getResolverList()) {
                logger.info("Publishing to Resolver {}", resolver);
                logger.debug("Using artifact patterns: {}", artifactPatterns);
                publishEngine.publish(moduleDescriptor,
                        artifactPatterns, (DependencyResolver) resolver, publishOptions);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> absoluteArtifactPatterns(List<String> absoluteArtifactPatterns, String relativeArtifactPattern, List<File> artifactParentDirs) {
        List<String> allArtifactPatterns = new ArrayList<String>(absoluteArtifactPatterns);
        for (File parentDir : artifactParentDirs) {
            allArtifactPatterns.add(new File(parentDir, relativeArtifactPattern).getAbsolutePath());
        }
        return allArtifactPatterns;
    }


}
