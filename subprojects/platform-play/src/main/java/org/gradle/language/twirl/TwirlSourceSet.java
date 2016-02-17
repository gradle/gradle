/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.twirl;

import org.gradle.api.Incubating;
import org.gradle.language.base.LanguageSourceSet;

/**
 * Represents a source set containing twirl templates
 */
@Incubating
public interface TwirlSourceSet extends LanguageSourceSet {
    /**
     * The default imports that should be added to generated source files
     */
    public TwirlImports getDefaultImports();

    /**
     * Sets the default imports that should be added to generated source files to the given language
     */
    public void setDefaultImports(TwirlImports defaultImports);
}
