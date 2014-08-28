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

import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.plugin.use.internal.PluginRequest;

import java.io.IOException;

public class PersistentCachingPluginResolutionServiceClient implements PluginResolutionServiceClient {

    public static final String PLUGIN_USE_METADATA_CACHE_NAME = "plugin-use-metadata";
    public static final String PLUGIN_USE_METADATA_OP_NAME = "queryPluginMetadata";
    public static final String CLIENT_STATUS_CACHE_NAME = "client-status";
    public static final String CLIENT_STATUS_OP_NAME = "queryClientStatus";

    private final PluginResolutionServiceClient delegate;
    private final PersistentCache cacheAccess;
    private final PersistentIndexedCache<PluginRequestKey, Response<PluginUseMetaData>> pluginUseMetadataCache;
    private final PersistentIndexedCache<ClientStatusKey, Response<ClientStatus>> clientStatusCache;

    public PersistentCachingPluginResolutionServiceClient(PluginResolutionServiceClient delegate, PersistentCache persistentCache) {
        this.delegate = delegate;
        this.cacheAccess = persistentCache;
        this.pluginUseMetadataCache = persistentCache.createCache(PersistentIndexedCacheParameters.of(
                        PLUGIN_USE_METADATA_CACHE_NAME, new PluginRequestKey.Serializer(), ResponseSerializer.of(new PluginUseMetaData.Serializer()))
        );
        this.clientStatusCache = persistentCache.createCache(PersistentIndexedCacheParameters.of(
                        CLIENT_STATUS_CACHE_NAME, new ClientStatusKey.Serializer(), ResponseSerializer.of(new ClientStatus.Serializer()))
        );
    }

    public Response<PluginUseMetaData> queryPluginMetadata(final String portalUrl, final boolean shouldValidate, final PluginRequest pluginRequest) {
        PluginRequestKey key = PluginRequestKey.of(portalUrl, pluginRequest);
        Factory<Response<PluginUseMetaData>> factory = new Factory<Response<PluginUseMetaData>>() {
            public Response<PluginUseMetaData> create() {
                return delegate.queryPluginMetadata(portalUrl, shouldValidate, pluginRequest);
            }
        };

        if (shouldValidate) {
            return fetch(PLUGIN_USE_METADATA_OP_NAME, pluginUseMetadataCache, key, factory);
        } else {
            return maybeFetch(PLUGIN_USE_METADATA_OP_NAME, pluginUseMetadataCache, key, factory);
        }
    }

    public Response<ClientStatus> queryClientStatus(final String portalUrl, final boolean shouldValidate, final String checksum) {
        ClientStatusKey key = new ClientStatusKey(portalUrl);
        Factory<Response<ClientStatus>> factory = new Factory<Response<ClientStatus>>() {
            public Response<ClientStatus> create() {
                return delegate.queryClientStatus(portalUrl, shouldValidate, checksum);
            }
        };

        if (shouldValidate) {
            return fetch(CLIENT_STATUS_OP_NAME, clientStatusCache, key, factory);
        } else {
            return maybeFetch(CLIENT_STATUS_OP_NAME, clientStatusCache, key, factory, new Spec<Response<ClientStatus>>() {
                public boolean isSatisfiedBy(Response<ClientStatus> element) {
                    return !element.getClientStatusChecksum().equals(checksum);
                }
            });
        }
    }

    private <K, V extends Response<?>> V maybeFetch(String operationName, final PersistentIndexedCache<K, V> cache, final K key, Factory<V> factory) {
        return maybeFetch(operationName, cache, key, factory, Specs.SATISFIES_NONE);
    }

    private <K, V extends Response<?>> V maybeFetch(String operationName, final PersistentIndexedCache<K, V> cache, final K key, Factory<V> factory, Spec<? super V> shouldFetch) {
        V cachedValue = cacheAccess.useCache(operationName + " - read", new Factory<V>() {
            public V create() {
                return cache.get(key);
            }
        });

        boolean fetch = cachedValue == null || shouldFetch.isSatisfiedBy(cachedValue);
        if (fetch) {
            return fetch(operationName, cache, key, factory);
        } else {
            return cachedValue;
        }
    }

    private <K, V extends Response<?>> V fetch(String operationName, final PersistentIndexedCache<K, V> cache, final K key, Factory<V> factory) {
        final V value = factory.create();
        if (value.isError()) {
            return value;
        }

        cacheAccess.useCache(operationName + " - write", new Runnable() {
            public void run() {
                cache.put(key, value);
            }
        });
        return value;
    }

    public void close() throws IOException {
        CompositeStoppable.stoppable(delegate, cacheAccess).stop();
    }

    private static class ResponseSerializer<T> implements Serializer<Response<T>> {

        private final Serializer<T> payloadSerializer;

        private static <T> ResponseSerializer<T> of(Serializer<T> payloadSerializer) {
            return new ResponseSerializer<T>(payloadSerializer);
        }

        private ResponseSerializer(Serializer<T> payloadSerializer) {
            this.payloadSerializer = payloadSerializer;
        }

        public Response<T> read(Decoder decoder) throws Exception {
            return new SuccessResponse<T>(
                    payloadSerializer.read(decoder),
                    decoder.readSmallInt(),
                    decoder.readString(),
                    decoder.readNullableString()
            );
        }

        public void write(Encoder encoder, Response<T> value) throws Exception {
            T response = value.getResponse();
            payloadSerializer.write(encoder, response);
            encoder.writeSmallInt(value.getStatusCode());
            encoder.writeString(value.getUrl());
            encoder.writeNullableString(value.getClientStatusChecksum());
        }
    }

    static class PluginRequestKey {
        private final String id;
        private final String version;

        private final String url;

        private static PluginRequestKey of(String url, PluginRequest pluginRequest) {
            return new PluginRequestKey(pluginRequest.getId().toString(), pluginRequest.getVersion(), url);
        }

        private PluginRequestKey(String id, String version, String url) {
            this.id = id;
            this.version = version;
            this.url = url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PluginRequestKey key = (PluginRequestKey) o;

            return id.equals(key.id) && url.equals(key.url) && version.equals(key.version);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + version.hashCode();
            result = 31 * result + url.hashCode();
            return result;
        }

        private static class Serializer implements org.gradle.messaging.serialize.Serializer<PluginRequestKey> {

            public PluginRequestKey read(Decoder decoder) throws Exception {
                return new PluginRequestKey(decoder.readString(), decoder.readString(), decoder.readString());
            }

            public void write(Encoder encoder, PluginRequestKey value) throws Exception {
                encoder.writeString(value.id);
                encoder.writeString(value.version);
                encoder.writeString(value.url);
            }
        }

    }

    public static class ClientStatusKey {

        private final String portalUrl;

        public ClientStatusKey(String portalUrl) {
            this.portalUrl = portalUrl;
        }

        public static class Serializer implements org.gradle.messaging.serialize.Serializer<ClientStatusKey> {
            public ClientStatusKey read(Decoder decoder) throws Exception {
                return new ClientStatusKey(decoder.readString());
            }

            public void write(Encoder encoder, ClientStatusKey value) throws Exception {
                encoder.writeString(value.portalUrl);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ClientStatusKey that = (ClientStatusKey) o;

            return portalUrl.equals(that.portalUrl);
        }

        @Override
        public int hashCode() {
            return portalUrl.hashCode();
        }
    }

}
