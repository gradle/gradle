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
package org.gradle.api.publication.maven.internal.ant;

import groovy.swing.factory.BeanFactory;
import groovy.util.FactoryBuilderSupport;
import org.apache.maven.artifact.ant.Authentication;
import org.apache.maven.artifact.ant.Proxy;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.artifact.ant.RepositoryPolicy;

public class RepositoryFactory extends BeanFactory {
    public RepositoryFactory(Class klass) {
        super(klass);
    }

    public RepositoryFactory(Class klass, boolean leaf) {
        super(klass, leaf);
    }

    public void setChild(FactoryBuilderSupport builder, Object parent, Object child) {
        if (child instanceof Authentication) {
            getRepository(parent).addAuthentication((Authentication) child);
        } else if (child instanceof Proxy) {
            getRepository(parent).addProxy((Proxy) child);
        } else if (child instanceof RepositoryPolicy) {
            if (builder.getCurrentName().equals("snapshots")) {
                getRepository(parent).addSnapshots((RepositoryPolicy) child);
            } else {
                getRepository(parent).addReleases((RepositoryPolicy) child);
            }
        }
    }

    private RemoteRepository getRepository(Object parent) {
        return (RemoteRepository) parent;
    }
}
