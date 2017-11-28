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

package org.gradle.buildinit.plugins.internal;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitBuildScriptDsl;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitBuildScriptDsl.KOTLIN;

/**
 * Assembles the parts of a build script.
 */
public class BuildScriptBuilder {
    private final List<String> headerLines = new ArrayList<String>();
    private final ListMultimap<String, DepSpec> dependencies = MultimapBuilder.linkedHashKeys().arrayListValues().build();
    private final Map<String, String> plugins = new LinkedHashMap<String, String>();
    private final List<ConfigSpec> configSpecs = new ArrayList<ConfigSpec>();

    /**
     * Adds a comment to the header of the file.
     */
    public BuildScriptBuilder fileComment(String comment) {
        headerLines.addAll(Splitter.on("\n").splitToList(comment));
        return this;
    }

    /**
     * Adds a plugin to be applied
     *
     * @param comment A description of why the plugin is required
     */
    public BuildScriptBuilder plugin(String comment, String pluginId) {
        plugins.put(pluginId, comment);
        return this;
    }

    /**
     * Adds one or more dependency to the specified configuration
     *
     * @param configuration The configuration where the dependency should be added
     * @param comment A description of why the dependencies are required
     * @param dependencies the dependencies
     */
    public BuildScriptBuilder dependency(String configuration, String comment, String... dependencies) {
        this.dependencies.put(configuration, new DepSpec(comment, Arrays.asList(dependencies)));
        return this;
    }

    /**
     * Adds one or more compile dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies
     */
    public BuildScriptBuilder compileDependency(String comment, String... dependencies) {
        return dependency("compile", comment, dependencies);
    }

    /**
     * Adds one or more test compile dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies
     */
    public BuildScriptBuilder testCompileDependency(String comment, String... dependencies) {
        return dependency("testCompile", comment, dependencies);
    }

    /**
     * Adds one or more test runtime dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies
     */
    public BuildScriptBuilder testRuntimeDependency(String comment, String... dependencies) {
        return dependency("testRuntime", comment, dependencies);
    }

    public BuildScriptBuilder taskMethodInvocation(String comment, String taskName, String taskType, String methodName) {
        return configuration(
            new TaskConfigurationSpec(taskName, taskType),
            new MethodInvocationSpec(comment, methodName));
    }

    public BuildScriptBuilder taskPropertyAssignment(String comment, String taskName, String taskType, String propertyName, Object propertyValue) {
        return configuration(
            new TaskConfigurationSpec(taskName, taskType),
            new PropertyAssignmentSpec(comment, propertyName, propertyValue));
    }

    public BuildScriptBuilder conventionPropertyAssignment(String comment, String conventionName, String propertyName, Object propertyValue) {
        return configuration(
            new ConventionConfigurationSpec(conventionName),
            new PropertyAssignmentSpec(comment, propertyName, propertyValue));
    }

    private BuildScriptBuilder configuration(ConfigBlockSpec configBlockSpec, ConfigCodeSpec configCodeSpec) {
        if (configSpecs.isEmpty()) {
            configSpecs.add(new ConfigSpec(configBlockSpec, configCodeSpec));
        } else {
            ConfigSpec previousConfigSpec = configSpecs.get(configSpecs.size() - 1);
            if (Objects.equal(previousConfigSpec.block, configBlockSpec)) {
                previousConfigSpec.codeSpecs.add(configCodeSpec);
            } else {
                configSpecs.add(new ConfigSpec(configBlockSpec, configCodeSpec));
            }
        }
        return this;
    }

    public TemplateOperation create(final BuildInitBuildScriptDsl dsl, final File target) {
        return new TemplateOperation() {
            @Override
            public void generate() {
                try {
                    PrintWriter writer = new PrintWriter(new FileWriter(target));
                    try {
                        // Generate the file header
                        writer.println("/*");
                        writer.println(" * This build file was generated by the Gradle 'init' task.");
                        if (!headerLines.isEmpty()) {
                            writer.println(" *");
                            for (String headerLine : headerLines) {
                                writer.println(" * " + headerLine);
                            }
                        }
                        writer.println(" */");

                        // Plugins
                        if (!plugins.isEmpty()) {
                            writer.println();
                            writer.println("plugins {");
                            for (Iterator<Map.Entry<String, String>> it = plugins.entrySet().iterator(); it.hasNext();) {
                                Map.Entry<String, String> entry = it.next();
                                writer.println("    // " + entry.getValue());
                                switch (dsl) {
                                    case KOTLIN:
                                        if (entry.getKey().matches("[a-z]+")) {
                                            writer.println("    " + entry.getKey());
                                        } else {
                                            writer.println("    `" + entry.getKey() + "`");
                                        }
                                        break;
                                    case GROOVY:
                                    default:
                                        writer.println("    id '" + entry.getKey() + "'");
                                }
                                if (it.hasNext()) {
                                    writer.println();
                                }
                            }
                            writer.println("}");
                        }

                        // Dependencies and repositories
                        if (!dependencies.isEmpty()) {
                            writer.println();
                            writer.println("// In this section you declare where to find the dependencies of your project");
                            writer.println("repositories {");
                            writer.println("    // Use jcenter for resolving your dependencies.");
                            writer.println("    // You can declare any Maven/Ivy/file repository here.");
                            writer.println("    jcenter()");
                            writer.println("}");
                            writer.println();
                            writer.println("dependencies {");
                            boolean firstDep = true;
                            for (String config : dependencies.keySet()) {
                                for (DepSpec depSpec : dependencies.get(config)) {
                                    if (firstDep) {
                                        firstDep = false;
                                    } else {
                                        writer.println();
                                    }
                                    writer.println("    // " + depSpec.comment);
                                    for (String dep : depSpec.deps) {
                                        switch (dsl) {
                                            case KOTLIN:
                                                writer.println("    " + config + "(\"" + dep + "\")");
                                                break;
                                            case GROOVY:
                                            default:
                                                writer.println("    " + config + " '" + dep + "'");
                                        }
                                    }
                                }
                            }
                            writer.println("}");
                        }

                        // Arbitrary configuration
                        for (ConfigSpec configSpec : configSpecs) {
                            writer.println();
                            configSpec.accept(new DefaultConfigSpecVisitor(dsl, writer));
                        }

                        writer.println();
                    } finally {
                        writer.close();
                    }
                } catch (Exception e) {
                    throw new GradleException("Could not generate file " + target + ".", e);
                }
            }
        };
    }

    private static class DepSpec {
        private final String comment;
        private final List<String> deps;

        DepSpec(String comment, List<String> deps) {
            this.comment = comment;
            this.deps = deps;
        }
    }

    private static class ConfigSpec {
        private final ConfigBlockSpec block;
        private List<ConfigCodeSpec> codeSpecs = new ArrayList<ConfigCodeSpec>();

        private ConfigSpec(ConfigBlockSpec block, ConfigCodeSpec codeSpec) {
            this.block = block;
            this.codeSpecs.add(codeSpec);
        }

        public void accept(ConfigSpecVisitor visitor) {
            visitor.visitOpenBlock(block);
            for (ConfigCodeSpec codeSpec : codeSpecs) {
                visitor.visitCode(codeSpec);
            }
            visitor.visitCloseBlock(block);
        }
    }

    private interface ConfigBlockSpec {
        @Nullable
        String getOpenCodeFor(BuildInitBuildScriptDsl dsl);

        @Nullable
        String getCloseCodeFor(BuildInitBuildScriptDsl dsl);
    }

    private interface ConfigCodeSpec {
        @Nullable
        String getComment();

        String getCodeFor(BuildInitBuildScriptDsl dsl);
    }

    private interface ConfigSpecVisitor {

        void visitOpenBlock(ConfigBlockSpec block);

        void visitCode(ConfigCodeSpec code);

        void visitCloseBlock(ConfigBlockSpec block);
    }

    private static class DefaultConfigSpecVisitor implements ConfigSpecVisitor {
        private final BuildInitBuildScriptDsl dsl;
        private final PrintWriter writer;

        private String indent = "";
        private boolean visitedCode = false;

        private DefaultConfigSpecVisitor(BuildInitBuildScriptDsl dsl, PrintWriter writer) {
            this.dsl = dsl;
            this.writer = writer;
        }


        @Override
        public void visitOpenBlock(ConfigBlockSpec block) {
            String openBlock = block.getOpenCodeFor(dsl);
            if (openBlock != null) {
                writer.println(openBlock);
                indent = "    ";
            }
        }

        @Override
        public void visitCode(ConfigCodeSpec code) {
            if (visitedCode) {
                writer.println();
            }
            String comment = code.getComment();
            if (comment != null) {
                for (String commentLine : comment.split("\n")) {
                    writer.println(indent + "// " + commentLine);
                }
            }
            for (String codeLine : code.getCodeFor(dsl).split("\n")) {
                writer.println(indent + codeLine);
            }
            visitedCode = true;
        }

        @Override
        public void visitCloseBlock(ConfigBlockSpec block) {
            String closeBlock = block.getCloseCodeFor(dsl);
            if (closeBlock != null) {
                writer.println(closeBlock);
                indent = "";
            }
        }
    }

    private static class TaskConfigurationSpec implements ConfigBlockSpec {
        private final String taskName;
        private final String taskType;

        private TaskConfigurationSpec(String taskName, String taskType) {
            this.taskName = taskName;
            this.taskType = taskType;
        }

        @Override
        public String getOpenCodeFor(BuildInitBuildScriptDsl dsl) {
            switch (dsl) {
                case KOTLIN:
                    return "val " + taskName + " by tasks.getting(" + taskType + "::class) {";
                case GROOVY:
                default:
                    return taskName + " {";
            }
        }

        @Override
        public String getCloseCodeFor(BuildInitBuildScriptDsl dsl) {
            return "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TaskConfigurationSpec that = (TaskConfigurationSpec) o;
            return Objects.equal(taskName, that.taskName) && Objects.equal(taskType, that.taskType);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(taskName, taskType);
        }
    }

    private static class ConventionConfigurationSpec implements ConfigBlockSpec {

        private final String conventionName;

        private ConventionConfigurationSpec(String conventionName) {
            this.conventionName = conventionName;
        }

        @Nullable
        @Override
        public String getOpenCodeFor(BuildInitBuildScriptDsl dsl) {
            if (dsl == KOTLIN) {
                return conventionName + " {";
            }
            return null;
        }

        @Nullable
        @Override
        public String getCloseCodeFor(BuildInitBuildScriptDsl dsl) {
            if (dsl == KOTLIN) {
                return "}";
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConventionConfigurationSpec that = (ConventionConfigurationSpec) o;
            return Objects.equal(conventionName, that.conventionName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(conventionName);
        }
    }

    private static class MethodInvocationSpec implements ConfigCodeSpec {
        private final String comment;
        private final String methodName;

        private MethodInvocationSpec(String comment, String methodName) {
            this.comment = comment;
            this.methodName = methodName;
        }

        @Override
        public String getComment() {
            return comment;
        }

        @Override
        public String getCodeFor(BuildInitBuildScriptDsl dsl) {
            return methodName + "()";
        }
    }

    private static class PropertyAssignmentSpec implements ConfigCodeSpec {

        private final String comment;
        private final String propertyName;
        private final Object propertyValue;

        private PropertyAssignmentSpec(String comment, String propertyName, Object propertyValue) {
            this.comment = comment;
            this.propertyName = propertyName;
            this.propertyValue = propertyValue;
        }

        @Override
        public String getComment() {
            return comment;
        }

        @Override
        public String getCodeFor(BuildInitBuildScriptDsl scriptDsl) {
            switch (scriptDsl) {
                case KOTLIN:
                    if (propertyValue instanceof Boolean) {
                        return "is" + StringUtils.capitalize(propertyName) + " = " + propertyValue;
                    } else if (propertyValue instanceof CharSequence) {
                        return propertyName + " = \"" + propertyValue + '\"';
                    }
                    return propertyName + " = " + propertyValue;
                case GROOVY:
                default:
                    if (propertyValue instanceof CharSequence) {
                        return propertyName + " = '" + propertyValue + "'";
                    }
                    return propertyName + " = " + propertyValue;
            }
        }
    }
}
