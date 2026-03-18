/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.tooling.model.build;

import org.gradle.api.Incubating;
import org.gradle.tooling.model.Model;
import org.jspecify.annotations.NullMarked;

/**
 * Provides user help content similar to what {@code gradle --help} provides on the CLI.
 *
 * @since 9.4.0
 */
@NullMarked
@Incubating
public interface Help extends Model {

    /**
     * Returns rendered help content similar to what {@code gradle --help} provides on the CLI.
     *
     * @since 9.4.0
     */
    String getRenderedText();
}
