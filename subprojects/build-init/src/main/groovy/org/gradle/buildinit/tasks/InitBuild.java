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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.internal.tasks.options.Option;
import org.gradle.api.internal.tasks.options.OptionValues;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.buildinit.plugins.internal.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.BuildInitTypeIds;
import org.gradle.buildinit.plugins.internal.ProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry;

import java.util.List;

/**
 * Generates a Gradle project structure.
 */
@Incubating
public class InitBuild extends DefaultTask {
    private String type;
    private String testFramework;

    @Internal
    private ProjectLayoutSetupRegistry projectLayoutRegistry;

    /**
     * The desired type of build to create, defaults to 'pom' if 'pom.xml' is found in project root if no pom.xml is found, it defaults to 'basic'.
     *
     * This property can be set via command-line option '--type'.
     */
    @Input
    public String getType() {
        return !Strings.isNullOrEmpty(type) ? type : getProject().file("pom.xml").exists() ? BuildInitTypeIds.POM : BuildInitTypeIds.BASIC;
    }

    /**
     * Alternative test framework to be used in the generated project.
     *
     * This property can be set via command-line option '--test-framework'
     */
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
        final String type = getType();
        BuildInitTestFramework testFramework = BuildInitTestFramework.fromName(getTestFramework());
        final ProjectLayoutSetupRegistry projectLayoutRegistry = getProjectLayoutRegistry();
        if (!projectLayoutRegistry.supports(type)) {
            String supportedTypes = Joiner.on(", ").join(Iterables.transform(projectLayoutRegistry.getSupportedTypes(), new Function<String, String>() {
                @Override
                public String apply(String input) {
                    return "'" + input + "'";
                }
            }));
            throw new GradleException("The requested build setup type '" + type + "' is not supported. Supported types: " + supportedTypes + ".");
        }

        ProjectInitDescriptor initDescriptor = projectLayoutRegistry.get(type);
        if (!testFramework.equals(BuildInitTestFramework.NONE) && !initDescriptor.supports(testFramework)) {
            throw new GradleException("The requested test framework '" + testFramework.getId() + "' is not supported in '" + type + "' setup type");
        }

        initDescriptor.generate(testFramework);
    }

    @Option(option = "type", description = "Set type of build to create.", order = 0)
    public void setType(String type) {
        this.type = type;
    }

    @OptionValues("type")
    @SuppressWarnings("unused")
    public List<String> getAvailableBuildTypes() {
        return getProjectLayoutRegistry().getSupportedTypes();
    }

    @Option(option = "test-framework", description = "Set alternative test framework to be used.", order = 1)
    public void setTestFramework(String testFramework) {
        this.testFramework = testFramework;
    }

    @OptionValues("test-framework")
    @SuppressWarnings("unused")
    public List<String> getAvailableTestFrameworks() {
        return BuildInitTestFramework.listSupported();
    }

    public void setProjectLayoutRegistry(ProjectLayoutSetupRegistry projectLayoutRegistry) {
        this.projectLayoutRegistry = projectLayoutRegistry;
    }

}
