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
package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.Convention;

public class SharedConventionRepositoryHandlerFactory implements Factory<RepositoryHandler> {
    private final Factory<? extends RepositoryHandler> factory;
    private final Convention convention;
    private ConventionMapping conventionMapping;

    public SharedConventionRepositoryHandlerFactory(Factory<? extends RepositoryHandler> factory, Convention convention) {
        this.factory = factory;
        this.convention = convention;
    }

    public RepositoryHandler create() {
        RepositoryHandler repositoryHandler = factory.create();
        IConventionAware conventionAwareHandler = (IConventionAware) repositoryHandler;
        if (conventionMapping == null) {
            conventionMapping = conventionAwareHandler.getConventionMapping();
            conventionMapping.setConvention(convention);
        } else {
            conventionAwareHandler.setConventionMapping(conventionMapping);
        }
        return repositoryHandler;
    }
}
