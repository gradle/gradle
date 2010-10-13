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
package org.gradle.logging.internal;

import org.gradle.api.logging.LogLevel;
import org.gradle.logging.StyledTextOutput;

public abstract class RenderableOutputEvent extends CategorisedOutputEvent {

    protected RenderableOutputEvent(long timestamp, String category, LogLevel logLevel) {
        super(timestamp, category, logLevel);
    }

    /**
     * Renders this event to the given output. The output's style will be set to {@link
     * org.gradle.logging.StyledTextOutput.Style#Normal}. The style will be reset after the rendering is complete, so
     * there is no need for this method to clean up the style.
     *
     * @param output The output to render to.
     */
    public abstract void render(StyledTextOutput output);
}
