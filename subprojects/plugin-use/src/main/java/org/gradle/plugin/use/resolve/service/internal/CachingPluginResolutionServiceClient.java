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

import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.BaseSerializerFactory;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.plugin.use.internal.PluginRequest;

public class CachingPluginResolutionServiceClient implements PluginResolutionServiceClient {

    private final PluginResolutionServiceClient delegate;
    private final PersistentCache cacheAccess;
    private final boolean invalidateCache;
    private final PersistentIndexedCache<Key, Response<PluginUseMetaData>> cache;

    public CachingPluginResolutionServiceClient(PluginResolutionServiceClient delegate, String cacheName, PersistentCache persistentCache, boolean invalidateCache) {
        this.delegate = delegate;
        this.cacheAccess = persistentCache;
        this.invalidateCache = invalidateCache;
        this.cache = persistentCache.createCache(new PersistentIndexedCacheParameters<Key, Response<PluginUseMetaData>>(cacheName, new KeySerializer(), new ResponseSerializer()));
    }

    public Response<PluginUseMetaData> queryPluginMetadata(PluginRequest pluginRequest, String portalUrl) {
        return get(pluginRequest, new Key(pluginRequest.getId().toString(), pluginRequest.getVersion(), portalUrl));
    }

    private Response<PluginUseMetaData> get(final PluginRequest pluginRequest, final Key key) {
        return cacheAccess.useCache("fetch plugin data", new Factory<Response<PluginUseMetaData>>() {
            public Response<PluginUseMetaData> create() {
                Response<PluginUseMetaData> response = null;
                if (invalidateCache) {
                    cache.remove(key);
                } else {
                    response = cache.get(key);
                }
                if (response == null) {
                    response = delegate.queryPluginMetadata(pluginRequest, key.url);
                    if (!response.isError()) {
                        cache.put(key, response);

                    }
                }

                return response;
            }
        });
    }

    static class Key {
        private final String id;
        private final String version;
        private final String url;

        private Key(String id, String version, String url) {
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

            Key key = (Key) o;

            return id.equals(key.id) && url.equals(key.url) && version.equals(key.version);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + version.hashCode();
            result = 31 * result + url.hashCode();
            return result;
        }
    }

    private static class KeySerializer implements Serializer<Key> {
        public Key read(Decoder decoder) throws Exception {
            return new Key(decoder.readString(), decoder.readString(), decoder.readString());
        }

        public void write(Encoder encoder, Key value) throws Exception {
            encoder.writeString(value.id);
            encoder.writeString(value.version);
            encoder.writeString(value.url);
        }
    }

    private static class ResponseSerializer implements Serializer<Response<PluginUseMetaData>> {

        public Response<PluginUseMetaData> read(Decoder decoder) throws Exception {
            return new SuccessResponse<PluginUseMetaData>(
                    new PluginUseMetaData(
                            decoder.readString(),
                            decoder.readString(),
                            BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER.read(decoder),
                            decoder.readString(),
                            decoder.readBoolean()
                    ),
                    decoder.readInt(),
                    decoder.readString()
            );
        }

        public void write(Encoder encoder, Response<PluginUseMetaData> value) throws Exception {
            PluginUseMetaData response = value.getResponse();
            encoder.writeString(response.id);
            encoder.writeString(response.version);
            BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER.write(encoder, response.implementation);
            encoder.writeString(response.implementationType);
            encoder.writeBoolean(response.legacy);
            encoder.writeInt(value.getStatusCode());
            encoder.writeString(value.getUrl());
        }
    }

}
