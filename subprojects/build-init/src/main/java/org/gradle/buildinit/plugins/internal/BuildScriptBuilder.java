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
import java.util.Collections;
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
    private final TopLevelBlock block = new TopLevelBlock();

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
        block.plugins.add(new PluginSpec(pluginId, null, comment));
        return this;
    }

    /**
     * Adds a plugin to be applied
     *
     * @param comment A description of why the plugin is required
     */
    public BuildScriptBuilder plugin(String comment, String pluginId, String version) {
        block.plugins.add(new PluginSpec(pluginId, version, comment));
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
     * Adds one or more implementation dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies
     */
    public BuildScriptBuilder implementationDependency(String comment, String... dependencies) {
        return dependency("implementation", comment, dependencies);
    }

    /**
     * Adds one or more test implementation dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies
     */
    public BuildScriptBuilder testImplementationDependency(String comment, String... dependencies) {
        return dependency("testImplementation", comment, dependencies);
    }

    /**
     * Adds one or more test runtime only dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies
     */
    public BuildScriptBuilder testRuntimeOnlyDependency(String comment, String... dependencies) {
        return dependency("testRuntimeOnly", comment, dependencies);
    }

    /**
     * Creates a method invocation expression, to use as a method argument or the RHS of a property assignment.
     */
    public Expression methodInvocationExpression(String methodName, Object... methodArgs) {
        return new MethodInvocationValue(methodName, expressionValues(methodArgs));
    }

    private List<ExpressionValue> expressionValues(Object... expressions) {
        List<ExpressionValue> result = new ArrayList<ExpressionValue>(expressions.length);
        for (Object expression : expressions) {
            result.add(expressionValue(expression));
        }
        return result;
    }

    private ExpressionValue expressionValue(Object expression) {
        if (expression instanceof CharSequence) {
            return new StringValue((CharSequence) expression);
        }
        if (expression instanceof ExpressionValue) {
            return (ExpressionValue) expression;
        }
        if (expression instanceof Number || expression instanceof Boolean) {
            return new LiteralValue(expression);
        }
        throw new IllegalArgumentException("Don't know how to treat " + expression + " as an expression.");
    }

    /**
     * Allows repositories to be added to this script.
     */
    public RepositoriesBuilder repositories() {
        return block.repositories;
    }

    /**
     * Allows statements to be added to the allprojects block.
     */
    public CrossConfigurationScriptBlockBuilder allprojects() {
        return block.allprojects;
    }

    /**
     * Allows statements to be added to the subprojects block.
     */
    public CrossConfigurationScriptBlockBuilder subprojects() {
        return block.subprojects;
    }

    /**
     * Adds a top level method invocation statement.
     */
    public BuildScriptBuilder methodInvocation(String comment, String methodName, Object... methodArgs) {
        return configuration(
            NULL_SELECTOR,
            new MethodInvocation(comment, new MethodInvocationValue(methodName, expressionValues(methodArgs))));
    }

    /**
     * Adds a top level property assignment statement.
     */
    public BuildScriptBuilder propertyAssignment(String comment, String propertyName, Object propertyValue) {
        return configuration(
            NULL_SELECTOR,
            new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
    }

    /**
     * Adds a method invocation statement to the configuration of a particular task.
     */
    public BuildScriptBuilder taskMethodInvocation(String comment, String taskName, String taskType, String methodName) {
        return configuration(
            new TaskSelector(taskName, taskType),
            new MethodInvocation(comment, new MethodInvocationValue(methodName)));
    }

    /**
     * Adds a property assignment statement to the configuration of a particular task.
     */
    public BuildScriptBuilder taskPropertyAssignment(String comment, String taskName, String taskType, String propertyName, Object propertyValue) {
        return configuration(
            new TaskSelector(taskName, taskType),
            new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
    }

    /**
     * Adds a property assignment statement to the configuration of all tasks of a particular type.
     */
    public BuildScriptBuilder taskPropertyAssignment(String comment, String taskType, String propertyName, Object propertyValue) {
        return configuration(
            new TaskTypeSelector(taskType),
            new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
    }

    /**
     * Adds a property assignment statement to the configuration of a particular convention.
     */
    public BuildScriptBuilder conventionPropertyAssignment(String comment, String conventionName, String propertyName, Object propertyValue) {
        return configuration(
            new ConventionSelector(conventionName),
            new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
    }

    private BuildScriptBuilder configuration(ConfigSelector selector, Statement statement) {
        block.configSpecs.add(new ConfigSpec(selector, statement));
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
                        block.writeBodyTo(printer);
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

    public interface Expression {
    }

    private interface ExpressionValue extends Expression {
        boolean isBooleanType();

        String with(Syntax syntax);
    }

    private static class StringValue implements ExpressionValue {
        final CharSequence value;

        StringValue(CharSequence value) {
            this.value = value;
        }

        @Override
        public boolean isBooleanType() {
            return false;
        }

        @Override
        public String with(Syntax syntax) {
            return syntax.string(value.toString());
        }
    }

    private static class LiteralValue implements ExpressionValue {
        final Object literal;

        LiteralValue(Object literal) {
            this.literal = literal;
        }

        @Override
        public boolean isBooleanType() {
            return literal instanceof Boolean;
        }

        @Override
        public String with(Syntax syntax) {
            return literal.toString();
        }
    }

    private static class MethodInvocationValue implements ExpressionValue {
        final String methodName;
        final List<ExpressionValue> arguments;

        MethodInvocationValue(String methodName, List<ExpressionValue> arguments) {
            this.methodName = methodName;
            this.arguments = arguments;
        }

        MethodInvocationValue(String methodName) {
            this(methodName, Collections.<ExpressionValue>emptyList());
        }

        @Override
        public boolean isBooleanType() {
            return false;
        }

        @Override
        public String with(Syntax syntax) {
            StringBuilder result = new StringBuilder();
            result.append(methodName);
            result.append("(");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    result.append(", ");
                }
                ExpressionValue argument = arguments.get(i);
                result.append(argument.with(syntax));
            }
            result.append(")");
            return result.toString();
        }
    }

    private static class PluginSpec extends ConfigExpression {
        final String id;
        @Nullable
        final String version;

        PluginSpec(String id, @Nullable String version, String comment) {
            super(comment);
            this.id = id;
            this.version = version;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.println(printer.syntax.pluginDependencySpec(id, version));
        }
    }

    public static class NestedPluginSpec extends PluginSpec {
        NestedPluginSpec(String id, @Nullable String version, String comment) {
            super(id, version, comment);
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.println(printer.syntax.nestedPluginDependencySpec(id, version));
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
         * The statement to be applied to the selected model object.
         */
        final Statement statement;

        ConfigSpec(ConfigSelector selector, Statement statement) {
            this.selector = selector;
            this.statement = statement;
        }
    }

    private interface ConfigSelector {
        int order();

        @Nullable
        String codeBlockSelectorFor(Syntax syntax);
    }

    private static final ConfigSelector NULL_SELECTOR = new ConfigSelector() {
        @Override
        public int order() {
            return 1;
        }

        @Override
        public String codeBlockSelectorFor(Syntax syntax) {
            return null;
        }
    };

    private static class TaskSelector implements ConfigSelector {

        final String taskName;
        final String taskType;

        private TaskSelector(String taskName, String taskType) {
            this.taskName = taskName;
            this.taskType = taskType;
        }

        @Override
        public int order() {
            return 5;
        }

        @Nullable
        @Override
        public String codeBlockSelectorFor(Syntax syntax) {
            return syntax.taskSelector(this);
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

    private static class TaskTypeSelector implements ConfigSelector {

        final String taskType;

        TaskTypeSelector(String taskType) {
            this.taskType = taskType;
        }

        @Override
        public int order() {
            return 4;
        }

        @Nullable
        @Override
        public String codeBlockSelectorFor(Syntax syntax) {
            return "tasks.withType(" + taskType + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TaskTypeSelector that = (TaskTypeSelector) o;
            return Objects.equal(taskType, that.taskType);
        }

        @Override
        public int hashCode() {
            return taskType.hashCode();
        }
    }

    private static class ConventionSelector implements ConfigSelector {

        final String conventionName;

        private ConventionSelector(String conventionName) {
            this.conventionName = conventionName;
        }

        @Override
        public int order() {
            return 3;
        }

        @Override
        public String codeBlockSelectorFor(Syntax syntax) {
            return syntax.conventionSelector(this);
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

    private interface Statement {
        @Nullable
        String getComment();

        boolean isEmpty();

        /**
         * Writes this statement to the given printer. Should not write the comment.
         */
        void writeCodeTo(PrettyPrinter printer);
    }

    private static abstract class ConfigExpression implements Statement {

        final String comment;

        ConfigExpression(@Nullable String comment) {
            this.comment = comment;
        }

        @Nullable
        @Override
        public String getComment() {
            return comment;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    private static class MethodInvocation extends ConfigExpression {

        final MethodInvocationValue invocationExpression;

        private MethodInvocation(String comment, MethodInvocationValue invocationExpression) {
            super(comment);
            this.invocationExpression = invocationExpression;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.println(invocationExpression.with(printer.syntax));
        }
    }

    private static class PropertyAssignment extends ConfigExpression {

        final String propertyName;
        final ExpressionValue propertyValue;

        private PropertyAssignment(String comment, String propertyName, ExpressionValue propertyValue) {
            super(comment);
            this.propertyName = propertyName;
            this.propertyValue = propertyValue;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.println(printer.syntax.propertyAssignment(this));
        }
    }

    private interface BlockBody {
        boolean isEmpty();

        void writeBodyTo(PrettyPrinter printer);
    }

    private static class BlockStatements implements BlockBody {
        final List<Statement> statements = new ArrayList<Statement>();

        BlockStatements() {
        }

        BlockStatements(Collection<? extends Statement> statements) {
            this.statements.addAll(statements);
        }

        BlockStatements(Statement statement) {
            this.statements.add(statement);
        }

        public void add(Statement statement) {
            statements.add(statement);
        }

        @Override
        public boolean isEmpty() {
            for (Statement statement : statements) {
                if (!statement.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void writeBodyTo(PrettyPrinter printer) {
            printer.printStatements(statements);
        }
    }

    private static class BlockStatement implements Statement {
        final String blockSelector;
        final BlockStatements body = new BlockStatements();

        BlockStatement(String blockSelector) {
            this.blockSelector = blockSelector;
        }

        @Nullable
        @Override
        public String getComment() {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return body.isEmpty();
        }

        void add(Statement statement) {
            body.add(statement);
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.printBlock(blockSelector, body);
        }
    }

    private static class RepositoriesBlock extends BlockStatement implements RepositoriesBuilder {
        RepositoriesBlock() {
            super("repositories");
        }

        @Override
        public void mavenLocal(String comment) {
            add(new MethodInvocation(comment, new MethodInvocationValue("mavenLocal")));
        }

        @Override
        public void maven(String comment, String url) {
            add(new MavenRepoExpression(comment, url));
        }
    }

    private static class MavenRepoExpression extends ConfigExpression {
        private final String url;

        MavenRepoExpression(@Nullable String comment, String url) {
            super(comment);
            this.url = url;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.printBlock("maven", new BlockStatements(new PropertyAssignment(null, "url", new StringValue(this.url))));
        }
    }

    private class ScriptBlockImpl implements ScriptBlockBuilder, BlockBody {
        final List<ConfigSpec> configSpecs = new ArrayList<ConfigSpec>();

        ScriptBlockBuilder configuration(ConfigSelector selector, Statement statement) {
            configSpecs.add(new ConfigSpec(selector, statement));
            return this;
        }

        @Override
        public boolean isEmpty() {
            return configSpecs.isEmpty();
        }

        @Override
        public void writeBodyTo(PrettyPrinter printer) {
            printer.printConfigSpecs(configSpecs);
        }

        @Override
        public void propertyAssignment(String comment, String propertyName, Object propertyValue) {
            configuration(NULL_SELECTOR, new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
        }

        @Override
        public void methodInvocation(String comment, String methodName, Object... methodArgs) {
            configuration(NULL_SELECTOR, new MethodInvocation(comment, new MethodInvocationValue(methodName, expressionValues(methodArgs))));
        }

        @Override
        public Expression propertyExpression(String value) {
            return new LiteralValue(value);
        }
    }

    private class CrossConfigBlock extends ScriptBlockImpl implements CrossConfigurationScriptBlockBuilder, Statement {
        final String blockName;
        final RepositoriesBlock repositories = new RepositoriesBlock();
        final List<Statement> plugins = new ArrayList<Statement>();
        final List<Statement> tasks = new ArrayList<Statement>();

        CrossConfigBlock(String blockName) {
            this.blockName = blockName;
        }

        @Override
        public RepositoriesBuilder repositories() {
            return repositories;
        }

        @Override
        public void plugin(String comment, String pluginId) {
            plugins.add(new NestedPluginSpec(pluginId, null, comment));
        }

        @Override
        public void taskPropertyAssignment(String comment, String taskType, String propertyName, Object propertyValue) {
            configuration(new TaskTypeSelector(taskType), new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
        }

        @Override
        public ScriptBlockBuilder taskRegistration(String comment, String taskName, String taskType) {
            TaskRegistration registration = new TaskRegistration(comment, taskName, taskType);
            tasks.add(registration);
            return registration.block;
        }

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && repositories.isEmpty() && plugins.isEmpty() && tasks.isEmpty();
        }

        @Nullable
        @Override
        public String getComment() {
            return null;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.printBlock(blockName, this);
        }

        @Override
        public void writeBodyTo(PrettyPrinter printer) {
            printer.printStatements(plugins);
            printer.printStatement(repositories);
            printer.printStatements(tasks);
            super.writeBodyTo(printer);
        }
    }

    private class TopLevelBlock extends ScriptBlockImpl {
        final BlockStatement plugins = new BlockStatement("plugins");
        final RepositoriesBlock repositories = new RepositoriesBlock();
        final CrossConfigBlock allprojects = new CrossConfigBlock("allprojects");
        final CrossConfigBlock subprojects = new CrossConfigBlock("subprojects");

        @Override
        public void writeBodyTo(PrettyPrinter printer) {
            printer.printStatement(plugins);
            printer.printStatement(allprojects);
            printer.printStatement(subprojects);
            printer.printStatement(repositories);
            super.writeBodyTo(printer);
        }
    }

    private class TaskRegistration implements Statement {
        final String taskName;
        final String taskType;
        final String comment;
        final ScriptBlockImpl block = new ScriptBlockImpl();

        TaskRegistration(String comment, String taskName, String taskType) {
            this.comment = comment;
            this.taskName = taskName;
            this.taskType = taskType;
        }

        @Nullable
        @Override
        public String getComment() {
            return comment;
        }

        @Override
        public boolean isEmpty() {
            return block.isEmpty();
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.printBlock(printer.syntax.taskRegistration(taskName, taskType), block);
        }
    }

    private static final class PrettyPrinter {

        private final Syntax syntax;
        private final PrintWriter writer;
        private String indent = "";
        private boolean firstExpression = false;

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
            firstExpression = true;
            for (String config : dependencies.keySet()) {
                for (DepSpec depSpec : dependencies.get(config)) {
                    printNewLineExceptTheFirstTime();
                    println("    // " + depSpec.comment);
                    for (String dep : depSpec.deps) {
                        println("    " + dependencySpec(config, dep));
                    }
                }
            }
            println("}");
            firstExpression = false;
        }

        public void printConfigSpecs(List<ConfigSpec> configSpecs) {
            if (configSpecs.isEmpty()) {
                return;
            }
            for (ConfigGroup group : sortedConfigGroups(configSpecs)) {
                printConfigGroup(group);
            }
        }

        private void printConfigGroup(ConfigGroup configGroup) {
            String blockSelector = configGroup.selector.codeBlockSelectorFor(syntax);
            if (blockSelector != null) {
                printNewLineExceptTheFirstTime();
                printBlock(blockSelector, configGroup);
            } else {
                printStatements(configGroup.statements);
            }
        }

        private List<ConfigGroup> sortedConfigGroups(List<ConfigSpec> configSpecs) {
            List<ConfigGroup> configGroups = configGroupsFrom(groupBySelector(configSpecs));
            sort(configGroups);
            return configGroups;
        }

        public void printBlock(String blockSelector, BlockBody blockBody) {
            if (blockBody.isEmpty()) {
                return;
            }

            String indentBefore = indent;

            println(blockSelector + " {");
            indent = indent + "    ";
            firstExpression = true;

            blockBody.writeBodyTo(this);

            indent = indentBefore;
            firstExpression = false;
            println("}");
        }

        public void printStatements(List<? extends Statement> statements) {
            for (Statement statement : statements) {
                printStatement(statement);
            }
        }

        private static class ConfigGroup extends BlockStatements implements Comparable<ConfigGroup> {

            /**
             * @see ConfigSpec#selector
             */
            final ConfigSelector selector;

            ConfigGroup(ConfigSelector selector, List<? extends Statement> statements) {
                super(statements);
                this.selector = selector;
            }

            @Override
            public int compareTo(ConfigGroup that) {
                return compareSelectors(this.selector, that.selector);
            }

            private int compareSelectors(ConfigSelector s1, ConfigSelector s2) {
                int diff = s1.order() - s2.order();
                if (diff < 0) {
                    return -1;
                }
                if (diff > 0) {
                    return 1;
                }
                if (s1 instanceof ConventionSelector) {
                    return conventionNameOf(s1).compareTo(conventionNameOf(s2));
                }
                if (s1 instanceof TaskSelector) {
                    return taskNameOf(s1).compareTo(taskNameOf(s2));
                }
                if (s1 instanceof TaskTypeSelector) {
                    return taskTypeOf(s1).compareTo(taskTypeOf(s2));
                }
                throw new IllegalStateException();
            }

            private String taskTypeOf(ConfigSelector selector) {
                return ((TaskTypeSelector) selector).taskType;
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

        private List<Statement> expressionsOf(Collection<ConfigSpec> specs) {
            return collect(specs, new Transformer<Statement, ConfigSpec>() {
                @Override
                public Statement transform(ConfigSpec configSpec) {
                    return configSpec.statement;
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

        private void printNewLineExceptTheFirstTime() {
            if (!firstExpression) {
                println();
            }
            firstExpression = false;
        }

        private void printStatement(Statement statement) {
            if (statement.isEmpty()) {
                return;
            }
            printNewLineExceptTheFirstTime();
            if (statement.getComment() != null) {
                println("// " + statement.getComment());
            }
            statement.writeCodeTo(this);
        }

        private String dependencySpec(String config, String notation) {
            return syntax.dependencySpec(config, notation);
        }

        private void println(String s) {
            if (!indent.isEmpty()) {
                writer.print(indent);
            }
            writer.println(s);
        }

        private void println() {
            writer.println();
        }
    }

    private interface Syntax {

        String pluginDependencySpec(String pluginId, @Nullable String version);

        String nestedPluginDependencySpec(String pluginId, @Nullable String version);

        String dependencySpec(String config, String notation);

        String propertyAssignment(PropertyAssignment expression);

        @Nullable
        String conventionSelector(ConventionSelector selector);

        String taskSelector(TaskSelector selector);

        String string(String string);

        String taskRegistration(String taskName, String taskType);
    }

    private static final class KotlinSyntax implements Syntax {
        @Override
        public String string(String string) {
            return '"' + string + '"';
        }

        @Override
        public String pluginDependencySpec(String pluginId, @Nullable String version) {
            if (version != null) {
                return "id(\"" + pluginId + "\").version(\"" + version + "\")";
            }
            return pluginId.matches("[a-z]+") ? pluginId : "`" + pluginId + "`";
        }

        @Override
        public String nestedPluginDependencySpec(String pluginId, @Nullable String version) {
            if (version != null) {
                throw new UnsupportedOperationException();
            }
            return "plugins.apply(\"" + pluginId + "\")";
        }

        @Override
        public String dependencySpec(String config, String notation) {
            return config + "(\"" + notation + "\")";
        }

        @Override
        public String propertyAssignment(PropertyAssignment expression) {
            String propertyName = expression.propertyName;
            ExpressionValue propertyValue = expression.propertyValue;
            if (propertyValue.isBooleanType()) {
                return booleanPropertyNameFor(propertyName) + " = " + propertyValue.with(this);
            }
            return propertyName + " = " + propertyValue.with(this);
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

        @Override
        public String taskRegistration(String taskName, String taskType) {
            return "val " + taskName + " by tasks.creating(" + taskType + "::class)";
        }
    }

    private static final class GroovySyntax implements Syntax {
        @Override
        public String string(String string) {
            return "'" + string + "'";
        }

        @Override
        public String pluginDependencySpec(String pluginId, @Nullable String version) {
            if (version != null) {
                return "id '" + pluginId + "' version '" + version + "'";
            }
            return "id '" + pluginId + "'";
        }

        @Override
        public String nestedPluginDependencySpec(String pluginId, @Nullable String version) {
            if (version != null) {
                throw new UnsupportedOperationException();
            }
            return "apply plugin: '" + pluginId + "'";
        }

        @Override
        public String dependencySpec(String config, String notation) {
            return config + " '" + notation + "'";
        }

        @Override
        public String propertyAssignment(PropertyAssignment expression) {
            String propertyName = expression.propertyName;
            ExpressionValue propertyValue = expression.propertyValue;
            return propertyName + " = " + propertyValue.with(this);
        }

        @Override
        public String conventionSelector(ConventionSelector selector) {
            return null;
        }

        @Override
        public String taskSelector(TaskSelector selector) {
            return selector.taskName;
        }

        @Override
        public String taskRegistration(String taskName, String taskType) {
            return "task " + taskName + "(type: " + taskType + ")";
        }
    }
}
