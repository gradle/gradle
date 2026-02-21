/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.java.archives.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

/**
 * Default implementation of {@link ManifestFactory}.
 */
public class DefaultManifestFactory implements ManifestFactory {
    private final ObjectFactory objectFactory;
    private final ProviderFactory providerFactory;
    private final FileResolver fileResolver;

    public DefaultManifestFactory(ObjectFactory objectFactory, ProviderFactory providerFactory, FileResolver fileResolver) {
        this.objectFactory = objectFactory;
        this.providerFactory = providerFactory;
        this.fileResolver = fileResolver;
    }

    @Override
    public DefaultManifest create() {
        return create(providerFactory.provider(() -> ManifestInternal.DEFAULT_CONTENT_CHARSET)).init();
    }

    @Override
    public DefaultManifest create(Provider<String> contentCharset) {
        return new DefaultManifest(fileResolver, objectFactory, providerFactory, this, contentCharset).init();
    }

    @Override
    public DefaultManifest readFrom(Object path, Provider<String> contentCharset) {
        return new DefaultManifest(fileResolver, objectFactory, providerFactory, this, contentCharset).read(path);
    }
}
