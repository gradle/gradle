/*
 * Copyright 2009 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.publish.PublishEngine;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultIvyDependencyPublisher implements IvyDependencyPublisher {
    public static final String FILE_PATH_EXTRA_ATTRIBUTE = "filePath";
    public static final List<String> ARTIFACT_PATTERN = WrapUtil.toList(String.format("[%s]", FILE_PATH_EXTRA_ATTRIBUTE));

    private static Logger logger = LoggerFactory.getLogger(DefaultIvyDependencyPublisher.class);

    private PublishOptionsFactory publishOptionsFactory;

    public DefaultIvyDependencyPublisher(PublishOptionsFactory publishOptionsFactory) {
        this.publishOptionsFactory = publishOptionsFactory;
    }

    public void publish(Set<String> configurations,
                        List<DependencyResolver> publishResolvers,
                        ModuleDescriptor moduleDescriptor,
                        File descriptorDestination,
                        PublishEngine publishEngine) {
        try {
            for (DependencyResolver resolver : publishResolvers) {
                logger.info("Publishing to Resolver {}", resolver);
                publishEngine.publish(moduleDescriptor, ARTIFACT_PATTERN, resolver,
                        publishOptionsFactory.createPublishOptions(configurations, descriptorDestination));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } 
    }
}
