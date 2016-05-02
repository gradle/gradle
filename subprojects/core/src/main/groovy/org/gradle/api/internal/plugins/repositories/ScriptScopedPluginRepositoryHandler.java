/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.plugins.repositories;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.dsl.PluginRepositoryHandler;
import org.gradle.internal.reflect.Instantiator;

import java.net.URI;
import java.util.Iterator;

/**
 * Bridges between a global PluginRepositoryHandler and a {@link org.gradle.api.Script}.
 */
public class ScriptScopedPluginRepositoryHandler implements PluginRepositoryHandler {
    private final PluginRepositoryHandler delegate;
    private final FileResolver fileResolver;
    private final Instantiator instantiator;

    public ScriptScopedPluginRepositoryHandler(PluginRepositoryHandler delegate, FileResolver fileResolver, Instantiator instantiator) {
        this.delegate = delegate;
        this.fileResolver = fileResolver;
        this.instantiator = instantiator;
    }

    @Override
    public MavenPluginRepository maven(Action<? super MavenPluginRepository> action) {
        final Action<? super MavenPluginRepository> localAction = action;
        return delegate.maven(new Action<MavenPluginRepository>() {
            @Override
            public void execute(MavenPluginRepository mavenPluginRepository) {
                MavenPluginRepositoryWrapper wrapper = instantiator.newInstance(
                    MavenPluginRepositoryWrapper.class, mavenPluginRepository, fileResolver);
                localAction.execute(wrapper);
            }
        });
    }

    @Override
    public IvyPluginRepository ivy(Action<? super IvyPluginRepository> action) {
        final Action<? super IvyPluginRepository> localAction = action;
        return delegate.ivy(new Action<IvyPluginRepository>() {
            @Override
            public void execute(IvyPluginRepository ivyPluginRepository) {
                IvyPluginRepositoryWrapper wrapper = instantiator.newInstance(
                    IvyPluginRepositoryWrapper.class, ivyPluginRepository, fileResolver);
                localAction.execute(wrapper);
            }
        });
    }

    @Override
    public GradlePluginPortal gradlePluginPortal() {
        return delegate.gradlePluginPortal();
    }

    @Override
    public Iterator<PluginRepository> iterator() {
        return delegate.iterator();
    }

    public static class MavenPluginRepositoryWrapper implements MavenPluginRepository {
        private final MavenPluginRepository delegate;
        private final FileResolver fileResolver;

        public MavenPluginRepositoryWrapper(MavenPluginRepository delegate, FileResolver fileResolver) {
            this.delegate = delegate;
            this.fileResolver = fileResolver;
        }

        @Override
        public URI getUrl() {
            return delegate.getUrl();
        }

        @Override
        public void setUrl(Object url) {
            delegate.setUrl(fileResolver.resolveUri(url));
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public void setName(String name) {
            delegate.setName(name);
        }

        @Override
        public PasswordCredentials getCredentials() {
            return delegate.getCredentials();
        }

        @Override
        public <T extends Credentials> T getCredentials(Class<T> credentialsType) {
            return delegate.getCredentials(credentialsType);
        }

        @Override
        public void credentials(Action<? super PasswordCredentials> action) {
            delegate.credentials(action);
        }

        @Override
        public <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) {
            delegate.credentials(credentialsType, action);
        }

        @Override
        public void authentication(Action<? super AuthenticationContainer> action) {
            delegate.authentication(action);
        }

        @Override
        public AuthenticationContainer getAuthentication() {
            return delegate.getAuthentication();
        }
    }

    public static class IvyPluginRepositoryWrapper implements IvyPluginRepository {
        private final IvyPluginRepository delegate;
        private final FileResolver fileResolver;

        public IvyPluginRepositoryWrapper(IvyPluginRepository delegate, FileResolver fileResolver) {
            this.delegate = delegate;
            this.fileResolver = fileResolver;
        }

        @Override
        public URI getUrl() {
            return delegate.getUrl();
        }

        @Override
        public void setUrl(Object url) {
            delegate.setUrl(fileResolver.resolveUri(url));
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public void setName(String name) {
            delegate.setName(name);
        }

        @Override
        public PasswordCredentials getCredentials() {
            return delegate.getCredentials();
        }

        @Override
        public <T extends Credentials> T getCredentials(Class<T> credentialsType) {
            return delegate.getCredentials(credentialsType);
        }

        @Override
        public void credentials(Action<? super PasswordCredentials> action) {
            delegate.credentials(action);
        }

        @Override
        public <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) {
            delegate.credentials(credentialsType, action);
        }

        @Override
        public void authentication(Action<? super AuthenticationContainer> action) {
            delegate.authentication(action);
        }

        @Override
        public AuthenticationContainer getAuthentication() {
            return delegate.getAuthentication();
        }
    }
}
