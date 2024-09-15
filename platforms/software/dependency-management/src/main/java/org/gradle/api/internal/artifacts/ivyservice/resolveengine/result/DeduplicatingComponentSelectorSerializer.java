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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A serializer for {@link ComponentSelector} that deduplicates the values and delegates to
 * another serializer for the actual serialization.
 * <p>
 * This serializer should not be reused to serialize multiple graphs.
 */
@NotThreadSafe
public class DeduplicatingComponentSelectorSerializer implements Serializer<ComponentSelector> {

    private final ComponentSelectorSerializer delegate;

    private final Map<ComponentSelector, Integer> writeIndex = new HashMap<>();
    private final List<ComponentSelector> readIndex = new ArrayList<>();

    public DeduplicatingComponentSelectorSerializer(ComponentSelectorSerializer delegate) {
        this.delegate = delegate;
    }

    @Override
    public ComponentSelector read(Decoder decoder) throws Exception {
        int idx = decoder.readSmallInt();
        ComponentSelector selector;
        if (idx == readIndex.size()) {
            // new entry
            selector = delegate.read(decoder);
            readIndex.add(selector);
        } else {
            selector = readIndex.get(idx);
        }
        return selector;
    }

    @Override
    public void write(Encoder encoder, ComponentSelector selector) throws Exception {
        Integer idx = writeIndex.get(selector);
        if (idx == null) {
            // new value
            int index = writeIndex.size();
            encoder.writeSmallInt(index);
            writeIndex.put(selector, index);
            delegate.write(encoder, selector);
        } else {
            // known value, only write index
            encoder.writeSmallInt(idx);
        }
    }

}
