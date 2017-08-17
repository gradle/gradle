/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.sink;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.LogGroupHeaderEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventGroup;
import org.gradle.internal.logging.events.OutputEventGroupListener;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.format.LogHeaderFormatter;

import javax.annotation.Nullable;
import java.util.List;

public class HeaderPrependingOutputEventGroupListener implements OutputEventGroupListener {
    private final OutputEventListener listener;
    private final LogHeaderFormatter logHeaderFormatter;
    @Nullable private Object lastRenderedGroupBuildOpId;

    public HeaderPrependingOutputEventGroupListener(OutputEventListener listener, LogHeaderFormatter logHeaderFormatter) {
        this.listener = listener;
        this.logHeaderFormatter = logHeaderFormatter;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof RenderableOutputEvent && lastRenderedGroupBuildOpId != null) {
            // Render a spacer line to separate this output from previously rendered group
            RenderableOutputEvent evt = (RenderableOutputEvent) event;
            listener.onOutput(new LogEvent(evt.getTimestamp(), evt.getCategory(), evt.getLogLevel(), "", null));
            lastRenderedGroupBuildOpId = null;
        }
        listener.onOutput(event);
    }

    @Override
    public void onOutput(OutputEventGroup eventGroup) {
        List<RenderableOutputEvent> renderableEvents = eventGroup.getLogs();
        if (!eventGroup.getBuildOperationId().equals(lastRenderedGroupBuildOpId)) {
            // TODO(EW): seems like we must check log level before determining emptiness
            listener.onOutput(header(eventGroup, !renderableEvents.isEmpty()));
        }
        for (RenderableOutputEvent event : renderableEvents) {
            listener.onOutput(event);
        }
        lastRenderedGroupBuildOpId = eventGroup.getBuildOperationId();
    }

    private StyledTextOutputEvent header(OutputEventGroup group, boolean hasLogs) {
        return new LogGroupHeaderEvent(
            group.getTimestamp(),
            group.getCategory(),
            LogLevel.LIFECYCLE,
            group.getBuildOperationId(),
            logHeaderFormatter.format(group.getLoggingHeader(), group.getDescription(), group.getShortDescription(), group.getStatus()),
            group.getBuildOperationCategory(),
            hasLogs);
    }
}
