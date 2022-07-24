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

package org.gradle.internal.logging.text;

import org.gradle.api.logging.LogLevel;

public abstract class AbstractStyledTextOutputFactory implements StyledTextOutputFactory {
    @Override
    public StyledTextOutput create(Class<?> logCategory) {
        return create(logCategory.getName());
    }

    @Override
    public StyledTextOutput create(String logCategory) {
        return create(logCategory, null);
    }

    @Override
    public StyledTextOutput create(Class<?> logCategory, LogLevel logLevel) {
        return create(logCategory.getName(), logLevel);
    }
}
