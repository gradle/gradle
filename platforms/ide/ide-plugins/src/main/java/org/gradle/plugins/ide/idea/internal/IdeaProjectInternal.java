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
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.IdeaProject;

import javax.annotation.Nullable;
import javax.inject.Inject;

@NonNullApi
public abstract class IdeaProjectInternal extends IdeaProject {

    @Inject
    public IdeaProjectInternal(Project project, XmlFileContentMerger ipr) {
        super(project, ipr);
    }

    public @Nullable IdeaLanguageLevel getRawLanguageLevel() {
        return languageLevel;
    }

    public @Nullable JavaVersion getRawTargetBytecodeVersion() {
        return targetBytecodeVersion;
    }
}
