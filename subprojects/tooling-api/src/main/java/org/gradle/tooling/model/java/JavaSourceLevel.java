/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.model.java;

import org.gradle.api.Incubating;

/**
 * Describes the source language level.
 *
 * @since 2.10
 */
@Incubating
public interface JavaSourceLevel {

    /**
     * Returns the java source level in a string-representation.
     * <p>
     * The result has the <major_version>.<minor_version> format. For Java 7 the result is "1.7".
     *
     * @return The Java source level. Never returns {@code null}.
     */
    String getLevel();
}
