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
package org.gradle.plugins.ide.idea.model;

import org.gradle.api.JavaVersion;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.plugins.ide.IdeWorkspace;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Enables fine-tuning project details of the IDEA plugin.
 * <p>
 * Example of use with a blend of all possible properties.
 * Typically you don't have to configure IDEA module directly because Gradle configures it for you.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 *     id 'idea'
 * }
 *
 * idea {
 *   project {
 *     //if you want to set specific jdk and language level
 *     jdkName = '1.6'
 *     languageLevel = '1.5'
 *
 *     //you can update the source wildcards
 *     wildcards += '!?*.ruby'
 *
 *     //you can configure the VCS used by the project
 *     vcs = 'Git'
 *
 *     //you can change the modules of the *.ipr
 *     //modules = project(':some-project').idea.module
 *
 *     //you can change the output file
 *     outputFile = new File(outputFile.parentFile, 'someBetterName.ipr')
 *   }
 * }
 * </pre>
 */
public abstract class IdeaProject implements IdeWorkspace {

    private final org.gradle.api.Project project;

    private List<IdeaModule> modules;
    private String jdkName;
    protected IdeaLanguageLevel languageLevel;
    protected JavaVersion targetBytecodeVersion;
    private String vcs;
    private Set<String> wildcards = new LinkedHashSet<>();
    private RegularFileProperty outputFile;

    @Inject
    public IdeaProject(org.gradle.api.Project project) {
        this.project = project;
        this.outputFile = project.getObjects().fileProperty();
    }

    @Override
    public String getDisplayName() {
        return "IDEA project";
    }

    @Override
    public Provider<RegularFile> getLocation() {
        return outputFile;
    }

    /**
     * An owner of this IDEA project.
     * <p>
     * If IdeaProject requires some information from gradle this field should not be used for this purpose.
     */
    public org.gradle.api.Project getProject() {
        return project;
    }

    /**
     * The name of the IDEA project. It is a convenience property that returns the name of the output file (without the file extension).
     * In IDEA, the project name is driven by the name of the 'ipr' file.
     */
    public String getName() {
        return getOutputFile().getName().replaceFirst("\\.ipr$", "");
    }

    /**
     * Modules for the ipr file.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    public List<IdeaModule> getModules() {
        return modules;
    }

    public void setModules(List<IdeaModule> modules) {
        this.modules = modules;
    }

    /**
     * The java version used for defining the project sdk.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    public String getJdkName() {
        return jdkName;
    }

    public void setJdkName(String jdkName) {
        this.jdkName = jdkName;
    }

    /**
     * The default Java language Level to use for this project.
     * <p>
     * Generally, it isn't recommended to change this value. Instead, you are encouraged to set {@code sourceCompatibility} and {@code targetCompatibility}
     * for your Gradle projects which allows you to have full control over language levels in Gradle projects, and means that Gradle and IDEA will use the same
     * settings when compiling.
     * <p>
     * When not explicitly set, this is calculated as the maximum language level for the Idea modules of this Idea project.
     */
    public IdeaLanguageLevel getLanguageLevel() {
        return languageLevel;
    }

    /**
     * Sets the java language level for the project.
     * <p>
     * When explicitly set in the build script, this setting overrides any calculated values for Idea project
     * and Idea module.
     *
     * @since 4.0
     */
    public void setLanguageLevel(IdeaLanguageLevel languageLevel) {
        this.languageLevel = languageLevel;
    }

    /**
     * Sets the java language level for the project.
     * Pass a valid Java version number (e.g. '1.5') or IDEA language level (e.g. 'JDK_1_5').
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     * <p>
     * When explicitly set in the build script, this setting overrides any calculated values for Idea project
     * and Idea module.
     */
    public void setLanguageLevel(Object languageLevel) {
        this.languageLevel = new IdeaLanguageLevel(languageLevel);
    }

    /**
     * The target bytecode version to use for this project.
     * <p>
     * Generally, it isn't recommended to change this value. Instead, you are encouraged to set {@code sourceCompatibility} and {@code targetCompatibility}
     * for your Gradle projects which allows you to have full control over language levels in Gradle projects, and means that Gradle and IDEA will use the same
     * settings when compiling.
     * <p>
     * When {@code languageLevel} is not explicitly set, this is calculated as the maximum target bytecode version for the Idea modules of this Idea project.
     */
    public JavaVersion getTargetBytecodeVersion() {
        return targetBytecodeVersion;
    }

    public void setTargetBytecodeVersion(JavaVersion targetBytecodeVersion) {
        this.targetBytecodeVersion = targetBytecodeVersion;
    }

    /**
     * The vcs for the project.
     * <p>
     * Values are the same as used in IDEA's "Version Control" preference window (e.g. 'Git', 'Subversion').
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    public String getVcs() {
        return vcs;
    }

    public void setVcs(String vcs) {
        this.vcs = vcs;
    }

    /**
     * The wildcard resource patterns.
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    public Set<String> getWildcards() {
        return wildcards;
    }

    public void setWildcards(Set<String> wildcards) {
        this.wildcards = wildcards;
    }

    /**
     * Output *.ipr
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    public File getOutputFile() {
        return outputFile.get().getAsFile();
    }

    public void setOutputFile(File outputFile) {
        this.outputFile.set(outputFile);
    }

}
