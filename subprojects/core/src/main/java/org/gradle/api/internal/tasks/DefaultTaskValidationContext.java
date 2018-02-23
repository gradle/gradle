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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.file.FileResolver;

import java.util.Collection;

public class DefaultTaskValidationContext implements TaskValidationContext {
    private final FileResolver resolver;
    private final Collection<String> messages;
    private Severity highestSeverity;

    public DefaultTaskValidationContext(FileResolver resolver, Collection<String> messages) {
        this.resolver = resolver;
        this.messages = messages;
        this.highestSeverity = Severity.WARNING;
    }

    @Override
    public FileResolver getResolver() {
        return resolver;
    }

    @Override
    public void recordValidationMessage(Severity severity, String message) {
        if (severity.compareTo(highestSeverity) > 0) {
            highestSeverity = severity;
        }
        messages.add(message);
    }

    @Override
    public Severity getHighestSeverity() {
        return highestSeverity;
    }
}
