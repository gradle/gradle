/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.api.JavaVersion;
import org.gradle.tooling.model.idea.IdeaProject;

import java.io.Serializable;

public class IdeaProjectJavaLanguageSettingsMixin {
    private final IdeaProject ideaProject;

    public IdeaProjectJavaLanguageSettingsMixin(IdeaProject ideaProject) {
        this.ideaProject = ideaProject;
    }

    public CompatibilityIdeaProjectJavaLanguageSettings getJavaLanguageSettings() {
        return new CompatibilityIdeaProjectJavaLanguageSettings(ideaProject);
    }

    public static class CompatibilityIdeaProjectJavaLanguageSettings implements Serializable {
        private final IdeaProject ideaProject;

        public CompatibilityIdeaProjectJavaLanguageSettings(IdeaProject ideaProject) {
            this.ideaProject = ideaProject;
        }

        public JavaVersion getLanguageLevel() {
            return JavaVersion.valueOf(ideaProject.getLanguageLevel().getLevel().replaceFirst("JDK", "VERSION"));
        }
    }
}
