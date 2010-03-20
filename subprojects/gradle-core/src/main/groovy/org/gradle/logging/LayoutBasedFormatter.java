/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import org.gradle.api.UncheckedIOException;

import java.io.IOException;

public class LayoutBasedFormatter implements LogEventFormatter {
    private final Layout<ILoggingEvent> layout;
    private final Appendable target;

    public LayoutBasedFormatter(Layout<ILoggingEvent> layout, Appendable target) {
        this.layout = layout;
        this.target = target;
    }

    public void format(ILoggingEvent event) {
        try {
            target.append(layout.doLayout(event));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
