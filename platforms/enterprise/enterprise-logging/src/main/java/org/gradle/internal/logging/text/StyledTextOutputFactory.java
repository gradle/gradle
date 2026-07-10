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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.Global.class)
public interface StyledTextOutputFactory {
    /**
     * Creates a {@code StyledTextOutput} with the given category and the standard output log level.
     *
     * @param logCategory The log category.
     * @return the output
     */
    StyledTextOutput create(String logCategory);

    /**
     * Creates a {@code StyledTextOutput} with the given category and the standard output log level.
     *
     * @param logCategory The log category.
     * @return the output
     */

    StyledTextOutput create(Class<?> logCategory);

    /**
     * Creates a {@code StyledTextOutput} with the given category and log level.
     *
     * @param logCategory The log category.
     * @param logLevel The log level. Can be null to use the standard output log level.
     * @return the output
     */
    StyledTextOutput create(Class<?> logCategory, LogLevel logLevel);

    /**
     * Creates a {@code StyledTextOutput} with the given category and log level.
     *
     * @param logCategory The log category.
     * @param logLevel The log level. Can be null to use the standard output log level.
     * @return the output
     */
    StyledTextOutput create(String logCategory, LogLevel logLevel);
}
