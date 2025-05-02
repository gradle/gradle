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

package org.gradle.plugins.ide.idea.internal;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaModuleIml;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

@NullMarked
public abstract class IdeaModuleInternal extends IdeaModule {

    @Inject
    public IdeaModuleInternal(Project project, IdeaModuleIml iml) {
        super(project, iml);
    }

    /**
     * Returns the user-defined value for the {@link #getLanguageLevel()} without triggering
     * the convention computation that is not compatible with Isolated Projects.
     */
    public @Nullable IdeaLanguageLevel getRawLanguageLevel() {
        return languageLevel;
    }

    /**
     * Returns the user-defined value for the {@link #getTargetBytecodeVersion()} without triggering
     * the convention computation that is not compatible with Isolated Projects.
     */
    public @Nullable JavaVersion getRawTargetBytecodeVersion() {
        return targetBytecodeVersion;
    }
}
