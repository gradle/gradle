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
import org.gradle.tooling.model.idea.IdeaModule;

import java.io.Serializable;

public class CompatibilityIdeaModuleMapping {

    private final IdeaModule ideaModule;

    public CompatibilityIdeaModuleMapping(IdeaModule ideaProject) {
        this.ideaModule = ideaProject;
    }

    public Object getJavaSourceSettings() {
        return new CompatibilityIdeaModuleJavaSourceSettings(ideaModule);
    }

    public static class CompatibilityIdeaModuleJavaSourceSettings implements Serializable {
        private final IdeaModule ideaModule;

        public CompatibilityIdeaModuleJavaSourceSettings(IdeaModule ideaModule) {
            this.ideaModule = ideaModule;
        }

        public JavaVersion getSourceLanguageLevel() {
            return JavaVersion.valueOf(ideaModule.getProject().getLanguageLevel().getLevel().replaceFirst("JDK", "VERSION"));
        }

        public boolean isSourceLanguageLevelInherited() {
            return true;
        }
    }
}
