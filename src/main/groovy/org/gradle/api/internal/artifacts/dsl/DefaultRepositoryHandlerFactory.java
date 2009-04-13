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
package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.ConventionValue;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultRepositoryHandlerFactory implements RepositoryHandlerFactory {
    private ResolverFactory repositoryFactory;
    private Convention convention;
    private Map<String, ConventionValue> conventionMapping = new HashMap<String, ConventionValue>();

    public DefaultRepositoryHandlerFactory(ResolverFactory repositoryFactory) {
        this.repositoryFactory = repositoryFactory;
    }

    public DefaultRepositoryHandler createRepositoryHandler() {
        DefaultRepositoryHandler repositoryHandler = new DefaultRepositoryHandler(repositoryFactory, convention);
        repositoryHandler.setConventionMapping(conventionMapping);
        return repositoryHandler;
    }

    public Map<String, ConventionValue> getConventionMapping() {
        return conventionMapping;
    }

    public void setConventionMapping(Map<String, ConventionValue> conventionMapping) {
        this.conventionMapping = conventionMapping;
    }

    public Convention getConvention() {
        return convention;
    }

    public void setConvention(Convention convention) {
        this.convention = convention;
    }
}
