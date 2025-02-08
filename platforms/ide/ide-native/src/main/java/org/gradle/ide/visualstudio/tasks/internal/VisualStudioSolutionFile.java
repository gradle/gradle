/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.tasks.internal;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Action;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.ide.visualstudio.TextProvider;
import org.gradle.plugins.ide.internal.generator.AbstractPersistableConfigurationObject;
import org.gradle.util.internal.TextUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject.getUUID;

public class VisualStudioSolutionFile extends AbstractPersistableConfigurationObject {

    private final List<Action<? super TextProvider>> actions = new ArrayList<>();
    private final Map<File, String> projects = new LinkedHashMap<>();
    private final Map<File, Set<ConfigurationSpec>> projectConfigurations = new LinkedHashMap<>();

    private String baseText;

    @Override
    protected String getDefaultResourceName() {
        return "default.sln";
    }

    public List<Action<? super TextProvider>> getActions() {
        return actions;
    }

    public void setProjects(List<ProjectSpec> projects) {
        for (ProjectSpec project : projects) {
            this.projects.put(project.projectFile, project.name);
            Set<ConfigurationSpec> configs = projectConfigurations.computeIfAbsent(project.projectFile, f -> new HashSet<>());
            configs.addAll(project.configurations);
        }
    }

    @Override
    public void load(InputStream inputStream) throws Exception {
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
            baseText = scanner.useDelimiter("\\A").next();
        }
    }

    @Override
    public void store(OutputStream outputStream) {
        SimpleTextProvider provider = new SimpleTextProvider();
        generateContent(provider.asBuilder());
        for (Action<? super TextProvider> action : actions) {
            action.execute(provider);
        }
        String text = TextUtil.convertLineSeparators(provider.getText(), TextUtil.getWindowsLineSeparator());
        Writer writer = new OutputStreamWriter(outputStream);
        try {
            writer.write(text);
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    private void generateContent(StringBuilder builder) {
        builder.append(baseText);
        for (Map.Entry<File, String> project : projects.entrySet()) {
            File projectFile = project.getKey();
            String projectName = project.getValue();
            builder.append(
                "\n" +
                    "Project(\"{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}\") = \"" + projectName + "\", \"" + projectFile.getAbsolutePath() + "\", \"" + getUUID(projectFile) + "\"\n" +
                    "EndProject"
            );
        }
        builder.append(
            "\n" +
                "Global\n" +
                "\tGlobalSection(SolutionConfigurationPlatforms) = preSolution"
        );

        Set<String> configurationNames = projectConfigurations.values().stream()
            .flatMap(set -> set.stream().map(spec -> spec.name))
            .collect(toCollection(TreeSet::new));
        for (String configurationName : configurationNames) {
            builder.append("\n\t\t").append(configurationName).append(" = ").append(configurationName);
        }
        builder.append(
            "\n" +
                "\tEndGlobalSection\n" +
                "\tGlobalSection(ProjectConfigurationPlatforms) = postSolution"
        );
        for (File projectFile : projects.keySet()) {
            List<String> configurations = configurationNames.stream()
                .flatMap(configurationName -> {
                    List<String> result = new ArrayList<>();
                    ConfigurationSpec configuration = projectConfigurations.get(projectFile).stream()
                        .filter(spec -> spec.name.equals(configurationName))
                        .findFirst().orElse(null);
                    ConfigurationSpec lastConfiguration = projectConfigurations.get(projectFile).stream()
                        .sorted(Comparator.comparing(ConfigurationSpec::getName))
                        .reduce((first, second) -> second).orElse(null);
                    if (configuration == null) {
                        result.add(configurationName + ".ActiveCfg = " + lastConfiguration.name);
                    } else {
                        result.add(configurationName + ".ActiveCfg = " + configuration.name);
                        if (configuration.buildable) {
                            result.add(configurationName + ".Build.0 = " + configuration.name);
                        }
                    }
                    return result.stream();
                })
                .collect(toList());

            for (String configuration : configurations) {
                builder.append("\n\t\t").append(getUUID(projectFile)).append(".").append(configuration);
            }
        }

        builder.append(
            "\n" +
                "\tEndGlobalSection\n" +
                "\tGlobalSection(SolutionProperties) = preSolution\n" +
                "\t\tHideSolutionNode = FALSE\n" +
                "\tEndGlobalSection\n" +
                "EndGlobal\n"
        );
    }

    private static class SimpleTextProvider implements TextProvider {

        private final StringBuilder builder = new StringBuilder();

        @Override
        public StringBuilder asBuilder() {
            return builder;
        }

        @Override
        public String getText() {
            return builder.toString();
        }

        @Override
        public void setText(String value) {
            builder.replace(0, builder.length(), value);
        }
    }

    public static class ConfigurationSpec {
        private final String name;
        private final boolean buildable;

        public ConfigurationSpec(String name, boolean buildable) {
            this.name = name;
            this.buildable = buildable;
        }

        @Input
        String getName() {
            return name;
        }

        @Input
        public boolean getBuildable() {
            return buildable;
        }
    }

    public static class ProjectSpec {
        private final String name;
        @VisibleForTesting
        final File projectFile;
        private final List<ConfigurationSpec> configurations;

        public ProjectSpec(String name, File projectFile, List<ConfigurationSpec> configurations) {
            this.name = name;
            this.projectFile = projectFile;
            this.configurations = configurations;
        }

        @Input
        public String getName() {
            return name;
        }

        @Input
        public String getProjectFilePath() {
            return projectFile.getAbsolutePath();
        }

        @Nested
        public List<ConfigurationSpec> getConfigurations() {
            return configurations;
        }
    }
}
