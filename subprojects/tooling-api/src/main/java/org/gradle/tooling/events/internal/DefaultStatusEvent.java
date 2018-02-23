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

package org.gradle.tooling.events.internal;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.StatusEvent;

/**
 * Base implementation of the {@code StatusEvent} interface.
 */
public class DefaultStatusEvent extends BaseProgressEvent implements StatusEvent {

    private final long total;
    private final long progress;
    private final String unit;

    public DefaultStatusEvent(long eventTime, String displayName, OperationDescriptor descriptor, long total, long progress, String unit) {
        super(eventTime, displayName, descriptor);
        this.total = total;
        this.progress = progress;
        this.unit = unit;
    }

    @Override
    public long getProgress() {
        return progress;
    }

    @Override
    public long getTotal() {
        return total;
    }

    @Override
    public String getUnit() {
        return unit;
    }
}
