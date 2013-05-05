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
package org.gradle.language.base.plugins;

import org.gradle.api.*;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.internal.DefaultBinariesContainer;
import org.gradle.language.base.internal.DefaultProjectSourceSet;

import javax.inject.Inject;

/**
 * Base plugin for language support.
 * Adds a {@link org.gradle.language.base.BinariesContainer} named {@code binaries} to the project.
 * Adds a {@link org.gradle.language.base.ProjectSourceSet} named {@code sources} to the project.
 */
@Incubating
public class LanguageBasePlugin implements Plugin<Project> {
    private final Instantiator instantiator;


    @Inject
    public LanguageBasePlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(Project target) {
        target.getExtensions().create("binaries", DefaultBinariesContainer.class, instantiator);
        target.getExtensions().create("sources", DefaultProjectSourceSet.class, instantiator);
    }
}
