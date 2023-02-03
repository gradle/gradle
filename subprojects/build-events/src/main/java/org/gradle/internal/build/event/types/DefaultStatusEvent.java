/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.tooling.internal.protocol.events.InternalStatusEvent;

public class DefaultStatusEvent extends AbstractProgressEvent<DefaultFileDownloadDescriptor> implements InternalStatusEvent {
    private final long progress;
    private final long total;
    private final String units;

    public DefaultStatusEvent(long eventTime, DefaultFileDownloadDescriptor descriptor, long progress, long total, String units) {
        super(eventTime, descriptor);
        this.progress = progress;
        this.total = total;
        this.units = units;
    }

    @Override
    public String getDisplayName() {
        return getDescriptor().getDisplayName();
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
    public String getUnits() {
        return units;
    }
}
