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
import org.gradle.internal.jvm.Jvm;
import org.gradle.tooling.model.idea.IdeaProject;

import java.io.File;
import java.io.Serializable;

public class CompatibilityIdeaProjectMapping {

    private final IdeaProject ideaProject;

    public CompatibilityIdeaProjectMapping(IdeaProject ideaProject) {
        this.ideaProject = ideaProject;
    }

    public Object getJavaSourceSettings() {
        return new CompatibilityIdeaProjectJavaSourceSettings(ideaProject);
    }

    public static class CompatibilityIdeaProjectJavaSourceSettings implements Serializable {
        private final IdeaProject ideaProject;

        public CompatibilityIdeaProjectJavaSourceSettings(IdeaProject ideaProject) {
            this.ideaProject = ideaProject;
        }

        public JavaVersion getSourceLanguageLevel() {
            return JavaVersion.valueOf(ideaProject.getLanguageLevel().getLevel().replaceFirst("JDK", "VERSION"));
        }

        public Object getTargetRuntime() {
            return new CompatibilityJavaRuntime();
        }

        public boolean isTargetRuntimeInherited() {
            return true;
        }
    }

    public static class CompatibilityJavaRuntime implements Serializable {

        public JavaVersion getJavaVersion() {
            return Jvm.current().getJavaVersion();
        }

        public File getHomeDirectory() {
            return Jvm.current().getJavaHome();
        }
    }
}
