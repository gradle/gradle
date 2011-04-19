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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.maven.MavenFactory;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.internal.artifacts.publish.maven.DefaultLocalMavenCacheLocator;
import org.gradle.api.internal.artifacts.publish.maven.DefaultMavenFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultResolverFactory;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;

public class DefaultDependencyManagementServices extends DefaultServiceRegistry implements DependencyManagementServices {
    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        super(parent);
    }

    protected ResolverFactory createResolverFactory() {
        return new DefaultResolverFactory(getFactory(LoggingManagerInternal.class), get(MavenFactory.class), new DefaultLocalMavenCacheLocator());
    }

    protected MavenFactory createMavenFactory() {
        return new DefaultMavenFactory();
    }
}
