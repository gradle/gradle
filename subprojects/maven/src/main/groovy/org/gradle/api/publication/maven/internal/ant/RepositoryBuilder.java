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
package org.gradle.api.publication.maven.internal.ant;

import groovy.util.FactoryBuilderSupport;
import org.apache.maven.artifact.ant.Authentication;
import org.apache.maven.artifact.ant.Proxy;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.artifact.ant.RepositoryPolicy;

public class RepositoryBuilder extends FactoryBuilderSupport {
    public RepositoryBuilder() {
        registerFactory("repository", new RepositoryFactory(RemoteRepository.class));
        registerBeanFactory("authentication", Authentication.class);
        registerBeanFactory("proxy", Proxy.class);
        registerBeanFactory("snapshots", RepositoryPolicy.class);
        registerBeanFactory("releases", RepositoryPolicy.class);
    }
}
