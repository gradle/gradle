/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.events.InternalTestMetadataDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestMetadataEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Provider-side implementation of test metadata event that carries key-values to the consumer.
 */
@NullMarked
public final class DefaultTestKeyValueDataMetadataEvent extends AbstractProgressEvent<InternalTestMetadataDescriptor> implements InternalTestMetadataEvent {
    private final Map<String, Object> values;

    public DefaultTestKeyValueDataMetadataEvent(long startTime, InternalTestMetadataDescriptor descriptor, Map<String, Object> values) {
        super(startTime, descriptor);
        this.values = values;
    }

    @Override
    public String getDisplayName() {
        return getDescriptor().getDisplayName() + " containing " + values.size() + " values";
    }

    @Override
    public Map<String, Object> getValues() {
        return values;
    }
}
