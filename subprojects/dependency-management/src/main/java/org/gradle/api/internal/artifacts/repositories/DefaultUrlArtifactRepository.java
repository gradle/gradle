/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories;

import org.gradle.api.artifacts.repositories.UrlArtifactRepository;
import org.gradle.api.internal.file.FileResolver;

import java.net.URI;

public class DefaultUrlArtifactRepository implements UrlArtifactRepository {

    private Object url;
    private boolean allowInsecureProtocol;
    private final FileResolver fileResolver;

    DefaultUrlArtifactRepository(final FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public URI getUrl() {
        return url == null ? null : fileResolver.resolveUri(url);
    }

    @Override
    public void setUrl(URI url) {
        this.url = url;
    }

    @Override
    public void setUrl(Object url) {
        this.url = url;
    }

    @Override
    public void allowInsecureProtocol(boolean allowInsecureProtocol) {
        this.allowInsecureProtocol = allowInsecureProtocol;
    }

    @Override
    public boolean isAllowInsecureProtocol() {
        return allowInsecureProtocol;
    }
}
