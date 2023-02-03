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

import org.gradle.internal.UncheckedException;
import org.gradle.vcs.git.GitVersionControlSpec;
import org.gradle.vcs.internal.spec.AbstractVersionControlSpec;

import java.net.URI;
import java.net.URISyntaxException;

public class DefaultGitVersionControlSpec extends AbstractVersionControlSpec implements GitVersionControlSpec {
    private URI url;

    @Override
    public URI getUrl() {
        return url;
    }

    @Override
    public void setUrl(URI url) {
        this.url = url;
    }

    @Override
    public void setUrl(String url) {
        // TODO - should use a resolver so that this method is consistent with Project.uri(string)
        try {
            setUrl(new URI(url));
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public String getDisplayName() {
        return "Git repository at " + getUrl();
    }

    @Override
    public String getUniqueId() {
        return "git-repo:" + getUrl().toASCIIString();
    }

    @Override
    public String getRepoName() {
        String[] pathParts = url.getPath().split("/");
        String repoPart = pathParts[pathParts.length-1];
        if (repoPart.endsWith(".git")) {
            repoPart = repoPart.substring(0, repoPart.indexOf(".git"));
        }
        return repoPart;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultGitVersionControlSpec that = (DefaultGitVersionControlSpec) o;

        return url != null ? url.equals(that.url) : that.url == null;
    }

    @Override
    public int hashCode() {
        return url != null ? url.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "GitVersionControlSpec{"
            + "url=" + url
            + '}';
    }
}
