/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.interfaces;


import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * A link to a documentation page.
 * <p>
 * Subtypes can represent different parts of the gradle documentation, e.g. the DSL reference, the user guide, etc.
 *
 * @since 8.4
 */
@Incubating
public interface DocLink {

    /**
     * The URL to the documentation page.
     */
    @Nullable
    String url();

    /**
     * A message that tells the user to consult the documentation.
     * There are currently 2 different messages used for this, hence this method.
     */
    @Nullable
    String consultDocumentationMessage();
}
