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
import org.gradle.api.Transformer;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.internal.file.PathToFileResolver;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.sort;
import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.groupBy;

/**
 * Assembles the parts of a build script.
 */
public class BuildScriptBuilder {

    private final BuildInitDsl dsl;
    private final PathToFileResolver fileResolver;
    private final String fileNameWithoutExtension;

    private final List<String> headerLines = new ArrayList<String>();
    private final ListMultimap<String, DepSpec> dependencies = MultimapBuilder.linkedHashKeys().arrayListValues().build();
    private final Map<String, String> plugins = new LinkedHashMap<String, String>();
    private final List<ConfigSpec> configSpecs = new ArrayList<ConfigSpec>();

    public BuildScriptBuilder(BuildInitDsl dsl, PathToFileResolver fileResolver, String fileNameWithoutExtension) {
        this.dsl = dsl;
        this.fileResolver = fileResolver;
        this.fileNameWithoutExtension = fileNameWithoutExtension;
    }

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

    public BuildScriptBuilder propertyAssignment(String comment, String propertyName, Object propertyValue) {
        return configuration(
            NULL_SELECTOR,
            new PropertyAssignment(comment, propertyName, propertyValue));
    }

    public BuildScriptBuilder taskMethodInvocation(String comment, String taskName, String taskType, String methodName) {
        return configuration(
            new TaskSelector(taskName, taskType),
            new MethodInvocation(comment, methodName));
    }

    public BuildScriptBuilder taskPropertyAssignment(String comment, String taskName, String taskType, String propertyName, Object propertyValue) {
        return configuration(
            new TaskSelector(taskName, taskType),
            new PropertyAssignment(comment, propertyName, propertyValue));
    }

    public BuildScriptBuilder conventionPropertyAssignment(String comment, String conventionName, String propertyName, Object propertyValue) {
        return configuration(
            new ConventionSelector(conventionName),
            new PropertyAssignment(comment, propertyName, propertyValue));
    }

    private BuildScriptBuilder configuration(ConfigSelector selector, ConfigExpression expression) {
        configSpecs.add(new ConfigSpec(selector, expression));
        return this;
    }

    public TemplateOperation create() {
        return new TemplateOperation() {
            @Override
            public void generate() {
                File target = getTargetFile();
                try {
                    PrintWriter writer = new PrintWriter(new FileWriter(target));
                    try {
                        PrettyPrinter printer = prettyPrinterFor(dsl, writer);
                        printer.printFileHeader(headerLines);
                        printer.printPlugins(plugins);
                        printer.printConfigSpecs(configSpecs);
                        if (!dependencies.isEmpty()) {
                            printer.printDependencies(dependencies);
                            printer.printRepositories();
                        }
                    } finally {
                        writer.close();
                    }
                } catch (Exception e) {
                    throw new GradleException("Could not generate file " + target + ".", e);
                }
            }
        };
    }

    private File getTargetFile() {
        return fileResolver.resolve(dsl.fileNameFor(fileNameWithoutExtension));
    }

    private static PrettyPrinter prettyPrinterFor(BuildInitDsl dsl, PrintWriter writer) {
        return new PrettyPrinter(syntaxFor(dsl), writer);
    }

    private static Syntax syntaxFor(BuildInitDsl dsl) {
        switch (dsl) {
            case KOTLIN:
                return new KotlinSyntax();
            case GROOVY:
                return new GroovySyntax();
            default:
                throw new IllegalStateException();
        }
    }

    private static class DepSpec {

        final String comment;
        final List<String> deps;

        DepSpec(String comment, List<String> deps) {
            this.comment = comment;
            this.deps = deps;
        }
    }

    /**
     * A configuration to be applied to a Gradle model object.
     */
    private static class ConfigSpec {

        /**
         * Selects the model object to be configured.
         */
        final ConfigSelector selector;

        /**
         * The configuration expression to be applied to the selected model object.
         */
        final ConfigExpression expression;

        ConfigSpec(ConfigSelector selector, ConfigExpression expression) {
            this.selector = selector;
            this.expression = expression;
        }
    }

    private interface ConfigSelector {
    }

    private static final ConfigSelector NULL_SELECTOR = new ConfigSelector() {
    };

    private static class TaskSelector implements ConfigSelector {

        final String taskName;
        final String taskType;

        private TaskSelector(String taskName, String taskType) {
            this.taskName = taskName;
            this.taskType = taskType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TaskSelector that = (TaskSelector) o;
            return Objects.equal(taskName, that.taskName) && Objects.equal(taskType, that.taskType);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(taskName, taskType);
        }
    }

    private static class ConventionSelector implements ConfigSelector {

        final String conventionName;

        private ConventionSelector(String conventionName) {
            this.conventionName = conventionName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConventionSelector that = (ConventionSelector) o;
            return Objects.equal(conventionName, that.conventionName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(conventionName);
        }
    }

    private static abstract class ConfigExpression {

        final String comment;

        ConfigExpression(@Nullable String comment) {
            this.comment = comment;
        }
    }

    private static class MethodInvocation extends ConfigExpression {

        final String methodName;

        private MethodInvocation(String comment, String methodName) {
            super(comment);
            this.methodName = methodName;
        }
    }

    private static class PropertyAssignment extends ConfigExpression {

        final String propertyName;
        final Object propertyValue;

        private PropertyAssignment(String comment, String propertyName, Object propertyValue) {
            super(comment);
            this.propertyName = propertyName;
            this.propertyValue = propertyValue;
        }
    }

    private static final class PrettyPrinter {

        private final Syntax syntax;
        private final PrintWriter writer;

        PrettyPrinter(Syntax syntax, PrintWriter writer) {
            this.syntax = syntax;
            this.writer = writer;
        }

        public void printFileHeader(Collection<String> lines) {
            println("/*");
            println(" * This file was generated by the Gradle 'init' task.");
            if (!lines.isEmpty()) {
                println(" *");
                for (String headerLine : lines) {
                    println(" * " + headerLine);
                }
            }
            println(" */");
        }

        public void printPlugins(Map<String, String> plugins) {
            if (plugins.isEmpty()) {
                return;
            }

            println();
            println("plugins {");
            for (Iterator<Map.Entry<String, String>> it = plugins.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, String> entry = it.next();
                String pluginId = entry.getKey();
                String comment = entry.getValue();
                println("    // " + comment);
                println("    " + pluginDependencySpec(pluginId));

                if (it.hasNext()) {
                    println();
                }
            }
            println("}");
        }

        public void printRepositories() {
            println();
            println("// In this section you declare where to find the dependencies of your project");
            println("repositories {");
            println("    // Use jcenter for resolving your dependencies.");
            println("    // You can declare any Maven/Ivy/file repository here.");
            println("    jcenter()");
            println("}");
        }

        public void printDependencies(ListMultimap<String, DepSpec> dependencies) {
            println();
            println("dependencies {");
            boolean firstDep = true;
            for (String config : dependencies.keySet()) {
                for (DepSpec depSpec : dependencies.get(config)) {
                    firstDep = printNewLineExceptTheFirstTime(firstDep);
                    println("    // " + depSpec.comment);
                    for (String dep : depSpec.deps) {
                        println("    " + dependencySpec(config, dep));
                    }
                }
            }
            println("}");
        }

        public void printConfigSpecs(List<ConfigSpec> configSpecs) {
            if (configSpecs.isEmpty()) {
                return;
            }
            for (ConfigGroup group : sortedConfigGroups(configSpecs)) {
                println();
                printConfigGroup(group);
            }
        }

        private void printConfigGroup(ConfigGroup configGroup) {
            String blockSelector = codeBlockSelectorFor(configGroup.selector);
            if (blockSelector != null) {
                println(blockSelector + " {");
                println();
            }

            String indent = blockSelector != null ? "    " : "";
            boolean firstExpression = true;
            for (ConfigExpression expression : configGroup.expressions) {
                firstExpression = printNewLineExceptTheFirstTime(firstExpression);
                printExpression(indent, expression);
            }

            if (blockSelector != null) {
                println("}");
            }
        }

        private List<ConfigGroup> sortedConfigGroups(List<ConfigSpec> configSpecs) {
            List<ConfigGroup> configGroups = configGroupsFrom(groupBySelector(configSpecs));
            sort(configGroups);
            return configGroups;
        }

        private static class ConfigGroup implements Comparable<ConfigGroup> {

            /**
             * @see ConfigSpec#selector
             */
            final ConfigSelector selector;
            final List<ConfigExpression> expressions;

            ConfigGroup(ConfigSelector selector, List<ConfigExpression> expressions) {
                this.selector = selector;
                this.expressions = expressions;
            }

            @Override
            public int compareTo(ConfigGroup that) {
                return compareSelectors(this.selector, that.selector);
            }

            private int compareSelectors(ConfigSelector s1, ConfigSelector s2) {
                if (NULL_SELECTOR == s1) {
                    return -1; // root statements come first
                }
                if (NULL_SELECTOR == s2) {
                    return 1; // root statements come first
                }
                if (s1 instanceof ConventionSelector) {
                    if (s2 instanceof ConventionSelector) {
                        return conventionNameOf(s1).compareTo(conventionNameOf(s2));
                    }
                    return -1; // conventions come first
                }
                if (s1 instanceof TaskSelector) {
                    if (s2 instanceof TaskSelector) {
                        return taskNameOf(s1).compareTo(taskNameOf(s2));
                    }
                    return 1; // tasks come last
                }
                throw new IllegalStateException();
            }

            private String conventionNameOf(ConfigSelector selector) {
                return ((ConventionSelector) selector).conventionName;
            }

            private String taskNameOf(ConfigSelector selector) {
                return ((TaskSelector) selector).taskName;
            }
        }

        private List<ConfigGroup> configGroupsFrom(Map<ConfigSelector, Collection<ConfigSpec>> groupedConfigSpecs) {
            ArrayList<ConfigGroup> result = new ArrayList<ConfigGroup>(groupedConfigSpecs.size());
            for (Map.Entry<ConfigSelector, Collection<ConfigSpec>> group : groupedConfigSpecs.entrySet()) {
                ConfigSelector selector = group.getKey();
                Collection<ConfigSpec> specs = group.getValue();
                result.add(new ConfigGroup(selector, expressionsOf(specs)));
            }
            return result;
        }

        private List<ConfigExpression> expressionsOf(Collection<ConfigSpec> specs) {
            return collect(specs, new Transformer<ConfigExpression, ConfigSpec>() {
                @Override
                public ConfigExpression transform(ConfigSpec configSpec) {
                    return configSpec.expression;
                }
            });
        }

        private Map<ConfigSelector, Collection<ConfigSpec>> groupBySelector(List<ConfigSpec> configSpecs) {
            return groupBy(configSpecs, new Transformer<ConfigSelector, ConfigSpec>() {
                @Override
                public ConfigSelector transform(ConfigSpec configSpec) {
                    return configSpec.selector;
                }
            });
        }

        private boolean printNewLineExceptTheFirstTime(boolean firstTime) {
            if (!firstTime) {
                println();
            }
            return false;
        }

        private void printExpression(String indent, ConfigExpression expression) {
            if (expression.comment != null) {
                println(indent + "// " + expression.comment);
            }
            println(indent + codeFor(expression));
        }

        @Nullable
        private String codeBlockSelectorFor(ConfigSelector selector) {
            if (NULL_SELECTOR == selector) {
                return null;
            }
            if (selector instanceof TaskSelector) {
                return taskSelector((TaskSelector) selector);
            }
            if (selector instanceof ConventionSelector) {
                return conventionSelector((ConventionSelector) selector);
            }
            throw new IllegalStateException();
        }

        String codeFor(ConfigExpression expression) {
            if (expression instanceof MethodInvocation) {
                return methodInvocation((MethodInvocation) expression);
            }
            if (expression instanceof PropertyAssignment) {
                return propertyAssignment((PropertyAssignment) expression);
            }
            throw new IllegalStateException();
        }

        private String methodInvocation(MethodInvocation expression) {
            return expression.methodName + "()";
        }

        @Nullable
        private String conventionSelector(ConventionSelector selector) {
            return syntax.conventionSelector(selector);
        }

        private String taskSelector(TaskSelector selector) {
            return syntax.taskSelector(selector);
        }

        private String propertyAssignment(PropertyAssignment expression) {
            return syntax.propertyAssignment(expression);
        }

        private String dependencySpec(String config, String notation) {
            return syntax.dependencySpec(config, notation);
        }

        private String pluginDependencySpec(String pluginId) {
            return syntax.pluginDependencySpec(pluginId);
        }

        private void println(String s) {
            writer.println(s);
        }

        private void println() {
            writer.println();
        }
    }

    private interface Syntax {

        String pluginDependencySpec(String pluginId);

        String dependencySpec(String config, String notation);

        String propertyAssignment(PropertyAssignment expression);

        @Nullable
        String conventionSelector(ConventionSelector selector);

        String taskSelector(TaskSelector selector);
    }

    private static final class KotlinSyntax implements Syntax {

        @Override
        public String pluginDependencySpec(String pluginId) {
            return pluginId.matches("[a-z]+") ? pluginId : "`" + pluginId + "`";
        }

        @Override
        public String dependencySpec(String config, String notation) {
            return config + "(\"" + notation + "\")";
        }

        @Override
        public String propertyAssignment(PropertyAssignment expression) {
            String propertyName = expression.propertyName;
            Object propertyValue = expression.propertyValue;
            if (propertyValue instanceof Boolean) {
                return booleanPropertyNameFor(propertyName) + " = " + propertyValue;
            }
            if (propertyValue instanceof CharSequence) {
                return propertyName + " = \"" + propertyValue + '\"';
            }
            return propertyName + " = " + propertyValue;
        }

        // In Kotlin:
        //
        // > Boolean accessor methods (where the name of the getter starts with is and the name of
        // > the setter starts with set) are represented as properties which have the same name as
        // > the getter method. Boolean properties are visibile with a `is` prefix in Kotlin
        //
        // https://kotlinlang.org/docs/reference/java-interop.html#getters-and-setters
        //
        // This code assumes all configurable Boolean property getters follow the `is` prefix convention.
        //
        private String booleanPropertyNameFor(String propertyName) {
            return "is" + StringUtils.capitalize(propertyName);
        }

        @Override
        public String conventionSelector(ConventionSelector selector) {
            return selector.conventionName;
        }

        @Override
        public String taskSelector(TaskSelector selector) {
            return "val " + selector.taskName + " by tasks.getting(" + selector.taskType + "::class)";
        }
    }

    private static final class GroovySyntax implements Syntax {

        @Override
        public String pluginDependencySpec(String pluginId) {
            return "id '" + pluginId + "'";
        }

        @Override
        public String dependencySpec(String config, String notation) {
            return config + " '" + notation + "'";
        }

        @Override
        public String propertyAssignment(PropertyAssignment expression) {
            String propertyName = expression.propertyName;
            Object propertyValue = expression.propertyValue;
            if (propertyValue instanceof CharSequence) {
                return propertyName + " = '" + propertyValue + "'";
            }
            return propertyName + " = " + propertyValue;
        }

        @Override
        public String conventionSelector(ConventionSelector selector) {
            return null;
        }

        @Override
        public String taskSelector(TaskSelector selector) {
            return selector.taskName;
        }
    }
}
