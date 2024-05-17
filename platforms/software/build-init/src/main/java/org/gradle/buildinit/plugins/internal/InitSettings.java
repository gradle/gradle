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

import org.gradle.api.Incubating;
import org.gradle.api.file.Directory;
import org.gradle.buildinit.InsecureProtocolOption;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static java.util.Optional.empty;

public class InitSettings {
    private final BuildInitDsl dsl;
    private final boolean useIncubatingAPIs;
    private final String packageName;
    private final BuildInitTestFramework testFramework;
    private final String projectName;
    private final List<String> subprojects;
    private final ModularizationOption modularizationOption;
    private final Directory target;
    private final InsecureProtocolOption insecureProtocolOption;
    private final Optional<JavaLanguageVersion> toolchainVersion;

    /**
     * Temporary constructor until we upgrade gradle/gradle to a nightly.
     *
     * This constructor needs to be left as-is because it's used in the gradle/gradle build to generate samples.
     * Changing it will break the Gradleception build (where we try to build Gradle with the latest version of Gradle).
     *
     * @see "/build-logic/build-init-samples/src/main/kotlin/gradlebuild/samples/SamplesGenerator.kt"
     */
    public InitSettings(
        String projectName, List<String> subprojects, ModularizationOption modularizationOption,
        BuildInitDsl dsl, String packageName, BuildInitTestFramework testFramework, Directory target
    ) {
        this(projectName, false, subprojects, modularizationOption, dsl, packageName, testFramework, InsecureProtocolOption.WARN, target, empty());
    }

    public InitSettings(
        String projectName, boolean useIncubatingAPIs, List<String> subprojects, ModularizationOption modularizationOption,
        BuildInitDsl dsl, String packageName, BuildInitTestFramework testFramework, Directory target
    ) {
        this(projectName, useIncubatingAPIs, subprojects, modularizationOption, dsl, packageName, testFramework, InsecureProtocolOption.WARN, target, empty());
    }

    public InitSettings(
        String projectName, boolean useIncubatingAPIs, List<String> subprojects, ModularizationOption modularizationOption,
        BuildInitDsl dsl, String packageName, BuildInitTestFramework testFramework, InsecureProtocolOption insecureProtocolOption, Directory target,
        Optional<JavaLanguageVersion> toolchainVersion
    ) {
        this.projectName = projectName;
        this.useIncubatingAPIs = useIncubatingAPIs;
        this.subprojects = getSubprojects(subprojects, modularizationOption);
        this.modularizationOption = modularizationOption;
        this.dsl = dsl;
        this.packageName = packageName;
        this.testFramework = testFramework;
        this.insecureProtocolOption = insecureProtocolOption;
        this.target = target;
        this.toolchainVersion = toolchainVersion;
    }

    private static List<String> getSubprojects(List<String> subprojects, ModularizationOption modularizationOption) {
        if (!subprojects.isEmpty() && modularizationOption == ModularizationOption.SINGLE_PROJECT) {
            return singletonList(subprojects.get(0));
        }
        return subprojects;
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

    @Nullable
    public InsecureProtocolOption getInsecureProtocolOption() {
        return insecureProtocolOption;
    }

    @Incubating
    public boolean isUseIncubatingAPIs() {
        return useIncubatingAPIs;
    }

    @Incubating
    public boolean isUseTestSuites() {
        return useIncubatingAPIs;
    }

    @Incubating
    public Optional<JavaLanguageVersion> getJavaLanguageVersion() {
        return toolchainVersion;
    }
}
