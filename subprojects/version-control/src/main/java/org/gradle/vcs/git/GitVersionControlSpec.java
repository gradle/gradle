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

package org.gradle.vcs.git;

import org.gradle.api.Incubating;
import org.gradle.internal.UncheckedException;
import org.gradle.vcs.VersionControlSpec;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A specification of a Git repository.
 */
@Incubating
public class GitVersionControlSpec implements VersionControlSpec {
    private URI url;

    /**
     * The URL for the repository in the specification.
     *
     * <p><b>Note:</b> The return value is a {@link URI} to avoid exposing the
     * full contract of {@link java.net.URL} clients of this interface.
     * Specifically, {@link java.net.URL} extends {@link URI} to add network
     * operations which are both unsuited for simple data specification and
     * allocate additional memory.</p>
     */
    public URI getUrl() {
        return url;
    }

    /**
     * Sets the URL of the repository.
     */
    public void setUrl(URI url) {
        this.url = url;
    }

    /**
     * Sets the URL of the repository.
     */
    public void setUrl(String url) {
        try {
            setUrl(new URI(url));
        } catch (URISyntaxException e) {
            throw new UncheckedException(e);
        }
    }

    @Override
    public String getDisplayName() {
        return "Git Repository at " + getUrl();
    }

    @Override
    public String getRepositoryId() {
        String host = url.getHost();
        if (host == null) {
            host = "local";
        }
        return host + "/" + url.getPath();
    }

    @Override
    public String getRepoName() {
        String[] pathParts = url.getPath().split("/");
        return pathParts[pathParts.length-1];
    }
}
