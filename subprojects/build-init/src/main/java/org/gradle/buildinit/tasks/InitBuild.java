/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.buildinit.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.internal.tasks.userinput.UserInputHandler;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.buildinit.plugins.internal.BuildConverter;
import org.gradle.buildinit.plugins.internal.BuildInitializer;
import org.gradle.buildinit.plugins.internal.InitSettings;
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.internal.text.TreeFormatter;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.gradle.buildinit.plugins.internal.PackageNameBuilder.toPackageName;

/**
 * Generates a Gradle project structure.
 */
public class InitBuild extends DefaultTask {
    private String type;
    private String dsl;
    private String testFramework;
    private String projectName;
    private String packageName;

    @Internal
    private ProjectLayoutSetupRegistry projectLayoutRegistry;

    /**
     * The desired type of project to generate, defaults to 'pom' if a 'pom.xml' is found in the project root and if no 'pom.xml' is found, it defaults to 'basic'.
     *
     * This property can be set via command-line option '--type'.
     */
    @Input
    public String getType() {
        return isNullOrEmpty(type) ? detectType() : type;
    }

    /**
     * The desired DSL of build scripts to create, defaults to 'groovy'.
     *
     * This property can be set via command-line option '--dsl'.
     *
     * @since 4.5
     */
    @Incubating
    @Optional
    @Input
    public String getDsl() {
        return isNullOrEmpty(dsl) ? BuildInitDsl.GROOVY.getId() : dsl;
    }

    /**
     * The name of the generated project, defaults to the name of the directory the project is generated in.
     *
     * This property can be set via command-line option '--project-name'.
     *
     * @since 5.0
     */
    @Incubating
    @Input
    public String getProjectName() {
        return projectName == null ? getProject().getProjectDir().getName() : projectName;
    }

    /**
     * The name of the package to use for generated source.
     *
     * This property can be set via command-line option '--package'.
     *
     * @since 5.0
     */
    @Incubating
    @Input
    public String getPackageName() {
        return packageName == null ? "" : packageName;
    }

    /**
     * The test framework to be used in the generated project.
     *
     * This property can be set via command-line option '--test-framework'
     */
    @Nullable
    @Optional
    @Input
    public String getTestFramework() {
        return testFramework;
    }

    public ProjectLayoutSetupRegistry getProjectLayoutRegistry() {
        if (projectLayoutRegistry == null) {
            projectLayoutRegistry = getServices().get(ProjectLayoutSetupRegistry.class);
        }

        return projectLayoutRegistry;
    }

    @TaskAction
    public void setupProjectLayout() {
        UserInputHandler inputHandler = getServices().get(UserInputHandler.class);
        ProjectLayoutSetupRegistry projectLayoutRegistry = getProjectLayoutRegistry();

        String type = null;
        if (isNullOrEmpty(this.type)) {
            BuildConverter converter = projectLayoutRegistry.getBuildConverter();
            if (converter.canApplyToCurrentDirectory()) {
                if (inputHandler.askYesNoQuestion("Found a " + converter.getSourceBuildDescription() + " build. Generate a Gradle build from this?", true)) {
                    type = converter.getId();
                }
            }
            if (type == null) {
                type = inputHandler.selectOption("Select type of project to generate", projectLayoutRegistry.getBuildGenerators(), projectLayoutRegistry.getDefault().getId());
            }
        } else {
            type = this.type;
        }

        BuildInitializer initDescriptor = projectLayoutRegistry.get(type);

        BuildInitDsl dsl;
        if (isNullOrEmpty(this.dsl)) {
            dsl = initDescriptor.getDefaultDsl();
            if (initDescriptor.getDsls().size() > 1) {
                dsl = inputHandler.selectOption("Select build script DSL", initDescriptor.getDsls(), dsl);
            }
        } else {
            dsl = BuildInitDsl.fromName(getDsl());
            if (!initDescriptor.getDsls().contains(dsl)) {
                throw new GradleException("The requested DSL '" + dsl + "' is not supported for '" + type + "' setup type");
            }
        }

        BuildInitTestFramework testFramework = null;
        if (isNullOrEmpty(this.testFramework)) {
            testFramework = initDescriptor.getDefaultTestFramework();
            if (initDescriptor.getTestFrameworks().size() > 1) {
                testFramework = inputHandler.selectOption("Select test framework", initDescriptor.getTestFrameworks(), testFramework);
            }
        } else {
            for (BuildInitTestFramework candidate : initDescriptor.getTestFrameworks()) {
                if (this.testFramework.equals(candidate.getId())) {
                    testFramework = candidate;
                    break;
                }
            }
            if (testFramework == null) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("The requested test framework '" + getTestFramework() + "' is not supported for '" + type + "' setup type. Supported frameworks");
                formatter.startChildren();
                for (BuildInitTestFramework framework : initDescriptor.getTestFrameworks()) {
                    formatter.node("'" + framework.getId() + "'");
                }
                formatter.endChildren();
                throw new GradleException(formatter.toString());
            }
        }

        String projectName = this.projectName;
        if (initDescriptor.supportsProjectName()) {
            if (isNullOrEmpty(projectName)) {
                projectName = inputHandler.askQuestion("Project name", getProjectName());
            }
        } else if (!isNullOrEmpty(projectName)) {
            throw new GradleException("Project name is not supported for '" + type + "' setup type.");
        }

        String packageName = this.packageName;
        if (initDescriptor.supportsPackage()) {
            if (isNullOrEmpty(packageName)) {
                packageName = inputHandler.askQuestion("Source package", toPackageName(projectName));
            }
        } else if (!isNullOrEmpty(packageName)) {
            throw new GradleException("Package name is not supported for '" + type + "' setup type.");
        }

        initDescriptor.generate(new InitSettings(projectName, dsl, packageName, testFramework));
    }

    @Option(option = "type", description = "Set the type of project to generate.")
    public void setType(String type) {
        this.type = type;
    }

    @OptionValues("type")
    @SuppressWarnings("unused")
    public List<String> getAvailableBuildTypes() {
        return getProjectLayoutRegistry().getAllTypes();
    }

    /**
     * Set the build script DSL to be used.
     *
     * @since 4.5
     */
    @Incubating
    @Option(option = "dsl", description = "Set the build script DSL to be used in generated scripts.")
    public void setDsl(String dsl) {
        this.dsl = dsl;
    }

    /**
     * Available build script DSLs to be used.
     *
     * @since 4.5
     */
    @Incubating
    @OptionValues("dsl")
    @SuppressWarnings("unused")
    public List<String> getAvailableDSLs() {
        return BuildInitDsl.listSupported();
    }

    /**
     * Set the test framework to be used.
     */
    @Option(option = "test-framework", description = "Set the test framework to be used.")
    public void setTestFramework(@Nullable String testFramework) {
        this.testFramework = testFramework;
    }

    /**
     * Available test frameworks.
     */
    @OptionValues("test-framework")
    @SuppressWarnings("unused")
    public List<String> getAvailableTestFrameworks() {
        return BuildInitTestFramework.listSupported();
    }

    /**
     * Set the project name.
     *
     * @since 5.0
     */
    @Incubating
    @Option(option = "project-name", description = "Set the project name.")
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Set the package name.
     *
     * @since 5.0
     */
    @Incubating
    @Option(option = "package", description = "Set the package for source files.")
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    void setProjectLayoutRegistry(ProjectLayoutSetupRegistry projectLayoutRegistry) {
        this.projectLayoutRegistry = projectLayoutRegistry;
    }

    private String detectType() {
        ProjectLayoutSetupRegistry projectLayoutRegistry = getProjectLayoutRegistry();
        BuildConverter buildConverter = projectLayoutRegistry.getBuildConverter();
        if (buildConverter.canApplyToCurrentDirectory()) {
            return buildConverter.getId();
        }
        return projectLayoutRegistry.getDefault().getId();
    }
}
