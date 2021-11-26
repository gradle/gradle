/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.attributes;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * Attributes to specific the type of source code contained in this variant.
 * <p>
 * This attribute is usually found on variants that have the {@link Category} attribute valued at {@link Category#SOURCES documentation}.
 *
 * @since 7.4
 */
@Incubating
public interface Sources extends Named {
    Attribute<Sources> SOURCES_ATTRIBUTE = Attribute.of("org.gradle.sources", Sources.class);

    /**
     * A list of directories containing source code, includes code in transitive dependencies
     */
    String ALL_SOURCE_DIRS = "all-source-directories";
}
