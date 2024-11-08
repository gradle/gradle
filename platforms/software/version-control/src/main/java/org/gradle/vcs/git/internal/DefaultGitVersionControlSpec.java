/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.git.internal;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.UncheckedException;
import org.gradle.vcs.git.GitVersionControlSpec;
import org.gradle.vcs.internal.spec.AbstractVersionControlSpec;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;

public abstract class DefaultGitVersionControlSpec extends AbstractVersionControlSpec implements GitVersionControlSpec {
    private final Property<URI> url;

    @Inject
    public DefaultGitVersionControlSpec(ObjectFactory objectFactory) {
        url = objectFactory.property(URI.class);
    }

    @Override
    public Property<URI> getUrl() {
        // TODO(lazy-migration Gradle 9.0): having setUrl(String) overload prevents us from simply declaring this method abstract.
        return url;
    }

    @Override
    public void setUrl(String url) {
        // TODO(lazy-migration Gradle 9.0): this should be handled as other Property<URI> instances.

        // TODO - should use a resolver so that this method is consistent with Project.uri(string)
        try {
            getUrl().set(new URI(url));
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public String getDisplayName() {
        return "Git repository at " + getUrl().get();
    }

    @Override
    public Provider<String> getUniqueId() {
        return getUrl().map(uri -> "git-repo:" + uri.toASCIIString());
    }

    @Override
    public Provider<String> getRepoName() {
        return getUrl().map(DefaultGitVersionControlSpec::extractRepoName);
    }

    private static String extractRepoName(URI url) {
        String[] pathParts = url.getPath().split("/");
        String repoPart = pathParts[pathParts.length-1];
        if (repoPart.endsWith(".git")) {
            repoPart = repoPart.substring(0, repoPart.indexOf(".git"));
        }
        return repoPart;
    }

    @Override
    public boolean equals(Object o) {
        // TODO(mlopatkin) we run equality checks in DefaultVcsMappingsStore.applyTo. Is it fine to resolve there?
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultGitVersionControlSpec that = (DefaultGitVersionControlSpec) o;

        URI url = getUrl().getOrNull();
        URI thatUrl = that.getUrl().getOrNull();
        return url != null ? url.equals(thatUrl) : thatUrl == null;
    }

    @Override
    public int hashCode() {
        URI url = getUrl().getOrNull();
        return url != null ? url.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "GitVersionControlSpec{"
            + "url=" + getUrl()
            + '}';
    }
}
