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
import org.gradle.api.DependencyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.text.ParseException;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyPublisher implements IDependencyPublisher {
    public static final String POM_FILE_NAME = "pom.xml";
    public static final String IVY_FILE_NAME = "ivy.xml";

    private static Logger logger = LoggerFactory.getLogger(DefaultDependencyPublisher.class);

    public void publish(List<String> configurations,
                        ResolverContainer resolvers,
                        ModuleDescriptor moduleDescriptor,
                        boolean uploadModuleDescriptor,
                        File parentDir,
                        DependencyManager dependencyManager,
                        PublishEngine publishEngine) {
        PublishOptions publishOptions = new PublishOptions();
        publishOptions.setOverwrite(true);
        publishOptions.setConfs(configurations.toArray(new String[configurations.size()]));
        File ivyFile = new File(parentDir, IVY_FILE_NAME);
        List<String> artifactPatterns = absoluteArtifactPatterns(
                dependencyManager.getAbsoluteArtifactPatterns(), dependencyManager.getDefaultArtifactPattern(), dependencyManager.getArtifactParentDirs());
        try {
            if (uploadModuleDescriptor) {
                moduleDescriptor.toIvyFile(ivyFile);
                publishOptions.setSrcIvyPattern(ivyFile.getAbsolutePath());
            }
            for (DependencyResolver resolver : resolvers.getResolverList()) {
                logger.info("Publishing to Resolver {}", resolver);
                logger.debug("Using artifact patterns: {}", artifactPatterns);
                publishEngine.publish(moduleDescriptor, artifactPatterns, resolver, publishOptions);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> absoluteArtifactPatterns(List<String> absoluteArtifactPatterns, String relativeArtifactPattern, Set<File> artifactParentDirs) {
        List<String> allArtifactPatterns = new ArrayList<String>(absoluteArtifactPatterns);
        for (File parentDir : artifactParentDirs) {
            allArtifactPatterns.add(new File(parentDir, relativeArtifactPattern).getAbsolutePath());
        }
        return allArtifactPatterns;
    }
}
