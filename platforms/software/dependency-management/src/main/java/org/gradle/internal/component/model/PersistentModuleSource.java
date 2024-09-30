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
package org.gradle.internal.component.model;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;

/**
 * Interface for module sources which need to be serialized with
 * the metadata descriptor. Adding or removing such a metadata source
 * requires updating the metadata cache store format (see {@link org.gradle.api.internal.artifacts.ivyservice.CacheLayout}).
 */
public interface PersistentModuleSource extends ModuleSource {
    /**
     * A <b>unique</b> source identifier for this module source. The
     * id must be &gt;=0 and represent the kind of module source.
     * For example, a module source may have different implementations,
     * but if they represent the same thing, they must return the same
     * id. The id is used during serialization to identify the kind of
     * source and select the appropriate serializer. This is _just_
     * a performance optimization, in order to avoid reflection during
     * serialization.
     *
     * @return the source id
     */
    int getCodecId();

    /**
     * A codec will be used by the metadata serializer to encode
     * and decode this module source. Codecs must be stateless as
     * they are shared between several metadata instances.
     *
     * Codecs must implement equals/hashcode.
     */
    interface Codec<T extends PersistentModuleSource> {
        void encode(T moduleSource, Encoder encoder) throws IOException;
        T decode(Decoder decoder) throws IOException;
    }
}
