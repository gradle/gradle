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

public class StreamingStyledTextOutputFactory extends AbstractStyledTextOutputFactory {
    private final Appendable target;

    public StreamingStyledTextOutputFactory(Appendable target) {
        this.target = target;
    }

    public StyledTextOutput create(String logCategory, LogLevel logLevel) {
        return new StreamingStyledTextOutput(target);
    }
}
