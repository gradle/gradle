/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.tasks;

import org.gradle.api.Buildable;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.file.SourceDirectorySet;

/**
 * A set of sources for a programming language.
 */
@Incubating
public interface LanguageSourceSet extends Named, Buildable {
    // TODO: do we want to keep using SourceDirectorySet in the new API?
    // would feel more natural if dirs could be added directly to LanguageSourceSet
    // could also think about extending SourceDirectorySet
    SourceDirectorySet getSource();
    FunctionalSourceSet getParent();
}
