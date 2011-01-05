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

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;

/**
 * @author Hans Dockter
 */
public class DefaultRepositoryHandlerFactory implements Factory<RepositoryHandler> {
    private final ResolverFactory repositoryFactory;
    private final ClassGenerator classGenerator;

    public DefaultRepositoryHandlerFactory(ResolverFactory repositoryFactory, ClassGenerator classGenerator) {
        this.repositoryFactory = repositoryFactory;
        this.classGenerator = classGenerator;
    }

    public DefaultRepositoryHandler create() {
        return classGenerator.newInstance(DefaultRepositoryHandler.class, repositoryFactory, classGenerator);
    }
}
