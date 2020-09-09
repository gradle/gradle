/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.file.Directory;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.Collections;
import java.util.List;

public class InitSettings {
    private final BuildInitDsl dsl;
    private final String packageName;
    private final BuildInitTestFramework testFramework;
    private final String projectName;
    private final List<String> subprojects;
    private final ModularizationOption modularizationOption;
    private final Directory target;

    public InitSettings(String projectName, List<String> subprojects, ModularizationOption modularizationOption,
                        BuildInitDsl dsl, String packageName, BuildInitTestFramework testFramework, Directory target) {
        this.projectName = projectName;
        this.subprojects = !subprojects.isEmpty() && modularizationOption == ModularizationOption.SINGLE_PROJECT ?
            Collections.singletonList(subprojects.get(0)) : subprojects;
        this.modularizationOption = modularizationOption;
        this.dsl = dsl;
        this.packageName = packageName;
        this.testFramework = testFramework;
        this.target = target;
    }

    public String getProjectName() {
        return projectName;
    }

    public List<String> getSubprojects() {
        return subprojects;
    }

    public ModularizationOption getModularizationOption() {
        return modularizationOption;
    }

    public BuildInitDsl getDsl() {
        return dsl;
    }

    public String getPackageName() {
        return packageName;
    }

    public BuildInitTestFramework getTestFramework() {
        return testFramework;
    }

    public Directory getTarget() {
        return target;
    }
}
