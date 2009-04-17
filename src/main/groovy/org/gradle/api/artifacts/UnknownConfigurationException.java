/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.UnknownDomainObjectException;

/**
 * <p>An {@code UnknownConfigurationException} is thrown when a configuration referenced by name cannot be found.</p>
 */
public class UnknownConfigurationException extends UnknownDomainObjectException {
    public UnknownConfigurationException(String message) {
        super(message);
    }
}
