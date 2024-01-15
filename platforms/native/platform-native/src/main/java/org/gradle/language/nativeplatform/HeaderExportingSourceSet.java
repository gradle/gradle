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
package org.gradle.language.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.language.base.LanguageSourceSet;

/**
 * A source set that exposes headers
 */
@Incubating
public interface HeaderExportingSourceSet extends LanguageSourceSet {
    /**
     * The headers as a directory set.
     */
    SourceDirectorySet getExportedHeaders();

    /**
     * The headers that are private to this source set and implicitly available. These are not explicitly made available for compilation.
     */
    SourceDirectorySet getImplicitHeaders();
}
