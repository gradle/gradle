/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.protocol;

/**
 * Signifies that the command completed successfully.
 * <p>
 * The value of the result is whatever the command produced on the server.
 * Its meaning depends on the original command.
 * <p>
 * This result type does permit {@code null} values. If it is an error for a certain
 * command to produce a {@code null} value that must be handled externally to this class.
 */
public class Success extends Result<Object> {

    public Success(Object value) {
        super(value);
    }
}