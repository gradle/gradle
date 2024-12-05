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

/**
 * Default implementation of the {@code InternalTestMetadataEvent} interface.
 *
 * This is created by the provider side of the tooling API.
 */
public final class DefaultTestMetadataEvent extends AbstractProgressEvent<InternalTestMetadataDescriptor> implements InternalTestMetadataEvent {
    private final Object metadata;

    public DefaultTestMetadataEvent(long startTime, InternalTestMetadataDescriptor descriptor, Object metadata) {
        super(startTime, descriptor);
        this.metadata = metadata;
    }

    @Override
    public String getDisplayName() {
        return getDescriptor().getDisplayName() + " " + metadata;
    }

    @Override
    public Object getMetadata() {
        return metadata;
    }
}
