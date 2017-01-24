/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.use.resolve.service.internal;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.internal.PluginRequest;

import java.io.IOException;

public class InMemoryCachingPluginResolutionServiceClient implements PluginResolutionServiceClient {

    private final Cache<Key<PluginIdentity>, Response<PluginUseMetaData>> pluginMetadataCache = CacheBuilder.newBuilder().build();
    private final Cache<Key<String>, Response<ClientStatus>> statusCache = CacheBuilder.newBuilder().build();
    private final PluginResolutionServiceClient delegate;

    public InMemoryCachingPluginResolutionServiceClient(PluginResolutionServiceClient delegate) {
        this.delegate = delegate;
    }

    public Response<PluginUseMetaData> queryPluginMetadata(final String portalUrl, final boolean shouldValidate, final PluginRequest pluginRequest) {
        Key<PluginIdentity> key = new Key<PluginIdentity>(portalUrl, new PluginIdentity(pluginRequest.getId(), pluginRequest.getVersion()));
        return getResponse(
                key,
                pluginMetadataCache,
                new Factory<Response<PluginUseMetaData>>() {
                    public Response<PluginUseMetaData> create() {
                        return delegate.queryPluginMetadata(portalUrl, shouldValidate, pluginRequest);
                    }
                },
                Transformers.constant(key)
        );
    }

    public Response<ClientStatus> queryClientStatus(final String portalUrl, final boolean shouldValidate, @Nullable final String checksum) {
        return getResponse(
                checksum == null ? null : new Key<String>(portalUrl, checksum),
                statusCache,
                new Factory<Response<ClientStatus>>() {
                    public Response<ClientStatus> create() {
                        return delegate.queryClientStatus(portalUrl, shouldValidate, checksum);
                    }
                },
                new Transformer<Key<String>, Response<ClientStatus>>() {
                    public Key<String> transform(Response<ClientStatus> original) {
                        return new Key<String>(portalUrl, original.getClientStatusChecksum());
                    }
                }
        );
    }

    private <K, V> Response<V> getResponse(Key<K> key, Cache<Key<K>, Response<V>> cache, Factory<Response<V>> responseFactory, Transformer<Key<K>, ? super Response<V>> keyGenerator) {
        Response<V> response = key == null ? null : cache.getIfPresent(key);
        if (response != null) {
            return response;
        } else {
            response = responseFactory.create();
            if (!response.isError()) {
                Key<K> actualKey = keyGenerator.transform(response);
                cache.put(actualKey, response);
            }
            return response;
        }
    }

    public void close() throws IOException {
        try {
            delegate.close();
        } finally {
            pluginMetadataCache.invalidateAll();
            statusCache.invalidateAll();
        }
    }

    public static class PluginIdentity {
        final PluginId pluginId;
        final String pluginVersion;

        public PluginIdentity(PluginId pluginId, String pluginVersion) {
            this.pluginId = pluginId;
            this.pluginVersion = pluginVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PluginIdentity that = (PluginIdentity) o;

            if (!pluginId.equals(that.pluginId)) {
                return false;
            }
            if (!pluginVersion.equals(that.pluginVersion)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = pluginId.hashCode();
            result = 31 * result + pluginVersion.hashCode();
            return result;
        }
    }

    public static class Key<T> {
        final String portalUrl;
        final T value;

        public Key(String portalUrl, T value) {
            this.portalUrl = portalUrl;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key key = (Key) o;

            if (!portalUrl.equals(key.portalUrl)) {
                return false;
            }
            if (!value.equals(key.value)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = portalUrl.hashCode();
            result = 31 * result + value.hashCode();
            return result;
        }
    }
}
