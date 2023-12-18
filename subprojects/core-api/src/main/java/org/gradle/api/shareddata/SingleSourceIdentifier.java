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

package org.gradle.api.shareddata;

import org.gradle.api.Incubating;
import org.gradle.util.Path;

/**
 * Identifies a single source project in queries to obtain shared data from a single project.
 *
 * @since 8.6
 */
@Incubating
public
interface SingleSourceIdentifier {
    /**
     * TBD
     * @since 8.6
     */
    Path getSourceProjectIdentitiyPath();
}
