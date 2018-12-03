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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the parts of a build script.
 */
public class BuildScriptBuilder {

    private final BuildInitDsl dsl;
    private final PathToFileResolver fileResolver;
    private final String fileNameWithoutExtension;

    private final List<String> headerLines = new ArrayList<String>();
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
        headerLines.addAll(splitComment(comment));
        return this;
    }

    private static List<String> splitComment(String comment) {
        return Splitter.on("\n").splitToList(comment.trim());
    }

    /**
     * Adds a plugin to be applied
     *
     * @param comment A description of why the plugin is required
     */
    public BuildScriptBuilder plugin(@Nullable String comment, String pluginId) {
        block.plugins.add(new PluginSpec(pluginId, null, comment));
        return this;
    }

    /**
     * Adds a plugin to be applied
     *
     * @param comment A description of why the plugin is required
     */
    public BuildScriptBuilder plugin(@Nullable String comment, String pluginId, String version) {
        block.plugins.add(new PluginSpec(pluginId, version, comment));
        return this;
    }

    /**
     * Adds one or more external dependencies to the specified configuration.
     *
     * @param configuration The configuration where the dependency should be added
     * @param comment A description of why the dependencies are required
     * @param dependencies the dependencies, in string notation
     */
    public BuildScriptBuilder dependency(String configuration, @Nullable String comment, String... dependencies) {
        dependencies().dependency(configuration, comment, dependencies);
        return this;
    }

    /**
     * Adds one or more external implementation dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies, in string notation
     */
    public BuildScriptBuilder implementationDependency(@Nullable String comment, String... dependencies) {
        return dependency("implementation", comment, dependencies);
    }

    /**
     * Adds one or more external test implementation dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies, in string notation
     */
    public BuildScriptBuilder testImplementationDependency(@Nullable String comment, String... dependencies) {
        return dependency("testImplementation", comment, dependencies);
    }

    /**
     * Adds one or more external test runtime only dependencies.
     *
     * @param comment A description of why the dependencies are required
     * @param dependencies The dependencies, in string notation
     */
    public BuildScriptBuilder testRuntimeOnlyDependency(@Nullable String comment, String... dependencies) {
        return dependency("testRuntimeOnly", comment, dependencies);
    }

    /**
     * Creates a method invocation expression, to use as a method argument or the RHS of a property assignment.
     */
    public Expression methodInvocationExpression(String methodName, Object... methodArgs) {
        return new MethodInvocationValue(methodName, expressionValues(methodArgs));
    }

    /**
     * Creates a property expression. to use as a method argument or the RHS of a property assignment.
     */
    public Expression propertyExpression(String value) {
        return new LiteralValue(value);
    }

    private static List<ExpressionValue> expressionValues(Object... expressions) {
        List<ExpressionValue> result = new ArrayList<ExpressionValue>(expressions.length);
        for (Object expression : expressions) {
            result.add(expressionValue(expression));
        }
        return result;
    }

    private static Map<String, ExpressionValue> expressionMap(Map<String, ?> expressions) {
        LinkedHashMap<String, ExpressionValue> result = new LinkedHashMap<String, ExpressionValue>();
        for (Map.Entry<String, ?> entry : expressions.entrySet()) {
            result.put(entry.getKey(), expressionValue(entry.getValue()));
        }
        return result;
    }

    private static ExpressionValue expressionValue(Object expression) {
        if (expression instanceof CharSequence) {
            return new StringValue((CharSequence) expression);
        }
        if (expression instanceof ExpressionValue) {
            return (ExpressionValue) expression;
        }
        if (expression instanceof Number || expression instanceof Boolean) {
            return new LiteralValue(expression);
        }
        if (expression instanceof Map) {
            return new MapLiteralValue(expressionMap((Map<String, ?>) expression));
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
     * Allows dependencies to be added to this script.
     */
    DependenciesBuilder dependencies() {
        return block.dependencies;
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
    public BuildScriptBuilder methodInvocation(@Nullable String comment, String methodName, Object... methodArgs) {
        block.methodInvocation(comment, methodName, methodArgs);
        return this;
    }

    /**
     * Adds a top level property assignment statement.
     */
    public BuildScriptBuilder propertyAssignment(@Nullable String comment, String propertyName, Object propertyValue) {
        block.propertyAssignment(comment, propertyName, propertyValue);
        return this;
    }

    /**
     * Adds a top level block statement.
     *
     * @return The body of the block, to which further statements can be added.
     */
    public ScriptBlockBuilder block(@Nullable String comment, String methodName) {
        return block.block(comment, methodName);
    }

    /**
     * Adds a method invocation statement to the configuration of a particular task.
     */
    public BuildScriptBuilder taskMethodInvocation(@Nullable String comment, String taskName, String taskType, String methodName, Object... methodArgs) {
        block.tasks.add(
            new TaskSelector(taskName, taskType),
            new MethodInvocation(comment, new MethodInvocationValue(methodName, expressionValues(methodArgs))));
        return this;
    }

    /**
     * Adds a property assignment statement to the configuration of a particular task.
     */
    public BuildScriptBuilder taskPropertyAssignment(@Nullable String comment, String taskName, String taskType, String propertyName, Object propertyValue) {
        block.tasks.add(
            new TaskSelector(taskName, taskType),
            new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
        return this;
    }

    /**
     * Adds a property assignment statement to the configuration of all tasks of a particular type.
     */
    public BuildScriptBuilder taskPropertyAssignment(@Nullable String comment, String taskType, String propertyName, Object propertyValue) {
        block.taskTypes.add(
            new TaskTypeSelector(taskType),
            new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
        return this;
    }

    /**
     * Registers a task.
     *
     * @return The body of the configuration action for the task.
     */
    public ScriptBlockBuilder taskRegistration(String comment, String taskName, String taskType) {
        TaskRegistration registration = new TaskRegistration(comment, taskName, taskType);
        block.tasksRegistrations.add(registration);
        return registration.body;
    }

    /**
     * Adds a property assignment statement to the configuration of a particular convention.
     */
    public BuildScriptBuilder conventionPropertyAssignment(@Nullable String comment, String conventionName, String propertyName, Object propertyValue) {
        block.conventions.add(
            new ConventionSelector(conventionName),
            new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
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

    private static class MapLiteralValue implements ExpressionValue {
        final Map<String, ExpressionValue> literal;

        public MapLiteralValue(Map<String, ExpressionValue> literal) {
            this.literal = literal;
        }

        @Override
        public boolean isBooleanType() {
            return false;
        }

        @Override
        public String with(Syntax syntax) {
            return syntax.mapLiteral(literal);
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
                ExpressionValue argument = arguments.get(i);
                if (i == 0) {
                    result.append(syntax.firstArg(argument));
                } else {
                    result.append(", ");
                    result.append(argument.with(syntax));
                }
            }
            result.append(")");
            return result.toString();
        }
    }

    private static class PluginSpec extends AbstractStatement {
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

    private static class DepSpec extends AbstractStatement {
        final String configuration;
        final List<String> deps;

        DepSpec(String configuration, String comment, List<String> deps) {
            super(comment);
            this.configuration = configuration;
            this.deps = deps;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            for (String dep : deps) {
                printer.println(printer.syntax.dependencySpec(configuration, printer.syntax.string(dep)));
            }
        }
    }

    private static class ProjectDepSpec extends AbstractStatement {
        private final String configuration;
        private final String projectPath;

        ProjectDepSpec(String configuration, String comment, String projectPath) {
            super(comment);
            this.configuration = configuration;
            this.projectPath = projectPath;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.println(printer.syntax.dependencySpec(configuration, "project(" + printer.syntax.string(projectPath) + ")"));
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
        @Nullable
        String codeBlockSelectorFor(Syntax syntax);
    }

    private static class TaskSelector implements ConfigSelector {

        final String taskName;
        final String taskType;

        private TaskSelector(String taskName, String taskType) {
            this.taskName = taskName;
            this.taskType = taskType;
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

    /**
     * Represents a statement in a script. Each statement has an optional comment that explains its purpose.
     */
    private interface Statement {
        enum Type {Empty, Single, Group}

        @Nullable
        String getComment();

        /**
         * Returns details of the size of this statement. Returns {@link Type#Empty} when this statement is empty and should not be included in the script.
         */
        Type type();

        /**
         * Writes this statement to the given printer. Should not write the comment. Called only when {@link #type()} returns a value != {@link Type#Empty}
         */
        void writeCodeTo(PrettyPrinter printer);
    }

    private static abstract class AbstractStatement implements Statement {

        final String comment;

        AbstractStatement(@Nullable String comment) {
            this.comment = comment;
        }

        @Nullable
        @Override
        public String getComment() {
            return comment;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Type type() {
            return Type.Single;
        }
    }

    private static class MethodInvocation extends AbstractStatement {

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

    private static class PropertyAssignment extends AbstractStatement {

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

    /**
     * Represents the contents of a block.
     */
    private interface BlockBody {
        void writeBodyTo(PrettyPrinter printer);
    }

    private static class StatementSequence implements Statement {
        final ScriptBlockImpl statements = new ScriptBlockImpl();

        public void add(Statement statement) {
            statements.add(statement);
        }

        @Nullable
        @Override
        public String getComment() {
            return null;
        }

        @Override
        public Statement.Type type() {
            return statements.type();
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            statements.writeBodyTo(printer);
        }
    }

    private static class BlockStatement implements Statement {
        private final String comment;
        final String blockSelector;
        final ScriptBlockImpl body = new ScriptBlockImpl();

        BlockStatement(String blockSelector) {
            this.comment = null;
            this.blockSelector = blockSelector;
        }

        BlockStatement(String comment, String blockSelector) {
            this.comment = comment;
            this.blockSelector = blockSelector;
        }

        @Nullable
        @Override
        public String getComment() {
            return comment;
        }

        @Override
        public Type type() {
            return body.type();
        }

        void add(Statement statement) {
            body.add(statement);
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.printBlock(blockSelector, body);
        }
    }

    private static class ScriptBlock extends BlockStatement {
        ScriptBlock(String comment, String methodName) {
            super(comment, methodName);
        }

        @Override
        public Type type() {
            // Always treat as non-empty
            return Type.Group;
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
        public void jcenter(@Nullable String comment) {
            add(new MethodInvocation(comment, new MethodInvocationValue("jcenter")));
        }

        @Override
        public void maven(String comment, String url) {
            add(new MavenRepoExpression(comment, url));
        }
    }

    private static class DependenciesBlock implements DependenciesBuilder, Statement, BlockBody {
        final ListMultimap<String, Statement> dependencies = MultimapBuilder.linkedHashKeys().arrayListValues().build();

        @Override
        public void dependency(String configuration, @Nullable String comment, String... dependencies) {
            this.dependencies.put(configuration, new DepSpec(configuration, comment, Arrays.asList(dependencies)));
        }

        @Override
        public void projectDependency(String configuration, @Nullable String comment, String projectPath) {
            this.dependencies.put(configuration, new ProjectDepSpec(configuration, comment, projectPath));
        }

        @Nullable
        @Override
        public String getComment() {
            return null;
        }

        @Override
        public Type type() {
            return dependencies.isEmpty() ? Type.Empty : Type.Group;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.printBlock("dependencies", this);
        }

        @Override
        public void writeBodyTo(PrettyPrinter printer) {
            for (String config : dependencies.keySet()) {
                for (Statement depSpec : dependencies.get(config)) {
                    printer.printStatement(depSpec);
                }
            }
        }
    }

    private static class MavenRepoExpression extends AbstractStatement {
        private final String url;

        MavenRepoExpression(@Nullable String comment, String url) {
            super(comment);
            this.url = url;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            ScriptBlockImpl statements = new ScriptBlockImpl();
            statements.propertyAssignment(null, "url", url);
            printer.printBlock("maven", statements);
        }
    }

    private static class ScriptBlockImpl implements ScriptBlockBuilder, BlockBody {
        final List<Statement> statements = new ArrayList<Statement>();

        public void add(Statement statement) {
            statements.add(statement);
        }

        public Statement.Type type() {
            for (Statement statement : statements) {
                if (statement.type() != Statement.Type.Empty) {
                    return Statement.Type.Group;
                }
            }
            return Statement.Type.Empty;
        }

        @Override
        public void writeBodyTo(PrettyPrinter printer) {
            printer.printStatements(statements);
        }

        @Override
        public void propertyAssignment(String comment, String propertyName, Object propertyValue) {
            statements.add(new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
        }

        @Override
        public void methodInvocation(String comment, String methodName, Object... methodArgs) {
            statements.add(new MethodInvocation(comment, new MethodInvocationValue(methodName, expressionValues(methodArgs))));
        }

        @Override
        public ScriptBlockBuilder block(String comment, String methodName) {
            ScriptBlock scriptBlock = new ScriptBlock(comment, methodName);
            statements.add(scriptBlock);
            return scriptBlock.body;
        }

        @Override
        public Expression propertyExpression(String value) {
            return new LiteralValue(value);
        }
    }

    private static class CrossConfigBlock extends ScriptBlockImpl implements CrossConfigurationScriptBlockBuilder, Statement {
        final String blockName;
        final RepositoriesBlock repositories = new RepositoriesBlock();
        final DependenciesBlock dependencies = new DependenciesBlock();
        final StatementSequence plugins = new StatementSequence();
        final StatementSequence taskRegistrations = new StatementSequence();
        final ConfigurationStatements<TaskTypeSelector> taskTypes = new ConfigurationStatements<TaskTypeSelector>();
        final ConfigurationStatements<TaskSelector> tasks = new ConfigurationStatements<TaskSelector>();
        final ConfigurationStatements<ConventionSelector> conventions = new ConfigurationStatements<ConventionSelector>();

        CrossConfigBlock(String blockName) {
            this.blockName = blockName;
        }

        @Override
        public RepositoriesBuilder repositories() {
            return repositories;
        }

        @Override
        public DependenciesBuilder dependencies() {
            return dependencies;
        }

        @Override
        public void plugin(String comment, String pluginId) {
            plugins.add(new NestedPluginSpec(pluginId, null, comment));
        }

        @Override
        public void taskPropertyAssignment(String comment, String taskType, String propertyName, Object propertyValue) {
            taskTypes.add(new TaskTypeSelector(taskType), new PropertyAssignment(comment, propertyName, expressionValue(propertyValue)));
        }

        @Override
        public void taskMethodInvocation(@Nullable String comment, String taskName, String taskType, String methodName, Object... methodArgs) {
            tasks.add(new TaskSelector(taskName, taskType), new MethodInvocation(comment, new MethodInvocationValue(methodName, expressionValues(methodArgs))));
        }

        @Override
        public ScriptBlockBuilder taskRegistration(String comment, String taskName, String taskType) {
            TaskRegistration registration = new TaskRegistration(comment, taskName, taskType);
            taskRegistrations.add(registration);
            return registration.body;
        }

        @Override
        public Type type() {
            if (super.type() == Type.Empty
                && repositories.type() == Type.Empty
                && plugins.type() == Type.Empty
                && taskRegistrations.type() == Type.Empty
                && conventions.type() == Type.Empty
                && tasks.type() == Type.Empty
                && taskTypes.type() == Type.Empty
                && dependencies.type() == Type.Empty) {
                return Type.Empty;
            }
            return Type.Group;
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
            printer.printStatement(plugins);
            printer.printStatement(repositories);
            printer.printStatement(dependencies);
            printer.printStatement(taskRegistrations);
            super.writeBodyTo(printer);
            printer.printStatement(conventions);
            printer.printStatement(taskTypes);
            printer.printStatement(tasks);
        }
    }

    private static class TopLevelBlock extends ScriptBlockImpl {
        final BlockStatement plugins = new BlockStatement("plugins");
        final StatementSequence tasksRegistrations = new StatementSequence();
        final RepositoriesBlock repositories = new RepositoriesBlock();
        final DependenciesBlock dependencies = new DependenciesBlock();
        final CrossConfigBlock allprojects = new CrossConfigBlock("allprojects");
        final CrossConfigBlock subprojects = new CrossConfigBlock("subprojects");
        final ConfigurationStatements<TaskTypeSelector> taskTypes = new ConfigurationStatements<TaskTypeSelector>();
        final ConfigurationStatements<TaskSelector> tasks = new ConfigurationStatements<TaskSelector>();
        final ConfigurationStatements<ConventionSelector> conventions = new ConfigurationStatements<ConventionSelector>();

        @Override
        public void writeBodyTo(PrettyPrinter printer) {
            printer.printStatement(plugins);
            printer.printStatement(allprojects);
            printer.printStatement(subprojects);
            printer.printStatement(repositories);
            printer.printStatement(dependencies);
            printer.printStatement(tasksRegistrations);
            super.writeBodyTo(printer);
            printer.printStatement(conventions);
            printer.printStatement(taskTypes);
            printer.printStatement(tasks);
        }
    }

    private static class TaskRegistration implements Statement {
        final String taskName;
        final String taskType;
        final String comment;
        final ScriptBlockImpl body = new ScriptBlockImpl();

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
        public Type type() {
            return Type.Group;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            printer.printBlock(printer.syntax.taskRegistration(taskName, taskType), body);
        }
    }

    private static class ConfigurationStatements<T extends ConfigSelector> implements Statement {
        final ListMultimap<T, Statement> blocks = MultimapBuilder.linkedHashKeys().arrayListValues().build();

        void add(T selector, Statement statement) {
            blocks.put(selector, statement);
        }

        @Nullable
        @Override
        public String getComment() {
            return null;
        }

        @Override
        public Type type() {
            return blocks.isEmpty() ? Type.Empty : Type.Single;
        }

        @Override
        public void writeCodeTo(PrettyPrinter printer) {
            for (T configSelector : blocks.keySet()) {
                String selector = configSelector.codeBlockSelectorFor(printer.syntax);
                if (selector != null) {
                    BlockStatement statement = new BlockStatement(selector);
                    statement.body.statements.addAll(blocks.get(configSelector));
                    printer.printStatement(statement);
                } else {
                    printer.printStatements(blocks.get(configSelector));
                }
            }
        }
    }

    private static final class PrettyPrinter {

        private final Syntax syntax;
        private final PrintWriter writer;
        private String indent = "";
        private boolean needSeparatorLine = true;
        private boolean firstStatementOfBlock = false;
        private boolean hasSeparatorLine = false;

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
                    if (headerLine.isEmpty()) {
                        println(" *");
                    } else {
                        println(" * " + headerLine);
                    }
                }
            }
            println(" */");
        }

        public void printBlock(String blockSelector, BlockBody blockBody) {
            String indentBefore = indent;

            println(blockSelector + " {");
            indent = indent + "    ";
            needSeparatorLine = false;
            firstStatementOfBlock = true;

            blockBody.writeBodyTo(this);

            indent = indentBefore;
            println("}");

            // Write a line separator after any block
            needSeparatorLine = true;
        }

        public void printStatements(List<? extends Statement> statements) {
            for (Statement statement : statements) {
                printStatement(statement);
            }
        }

        private void printStatementSeparator() {
            if (needSeparatorLine && !hasSeparatorLine) {
                println();
                needSeparatorLine = false;
            }
        }

        private void printStatement(Statement statement) {
            Statement.Type type = statement.type();
            if (type == Statement.Type.Empty) {
                return;
            }

            boolean hasComment = statement.getComment() != null;

            // Add separators before and after anything with a comment or that is a block or group of statements
            boolean needsSeparator = hasComment || type == Statement.Type.Group;
            if (needsSeparator && !firstStatementOfBlock) {
                needSeparatorLine = true;
            }

            printStatementSeparator();

            if (hasComment) {
                for (String line : splitComment(statement.getComment())) {
                    println("// " + line);
                }
            }

            statement.writeCodeTo(this);

            firstStatementOfBlock = false;
            if (needsSeparator) {
                needSeparatorLine = true;
            }
        }

        private void println(String s) {
            if (!indent.isEmpty()) {
                writer.print(indent);
            }
            writer.println(s);
            hasSeparatorLine = false;
        }

        private void println() {
            writer.println();
            hasSeparatorLine = true;
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

        String mapLiteral(Map<String, ExpressionValue> map);

        String firstArg(ExpressionValue argument);
    }

    private static final class KotlinSyntax implements Syntax {
        @Override
        public String string(String string) {
            return '"' + string + '"';
        }

        @Override
        public String mapLiteral(Map<String, ExpressionValue> map) {
            StringBuilder builder = new StringBuilder();
            builder.append("mapOf(");
            boolean first = true;
            for (Map.Entry<String, ExpressionValue> entry : map.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    builder.append(", ");
                }
                builder.append(string(entry.getKey()));
                builder.append(" to ");
                builder.append(entry.getValue().with(this));
            }
            builder.append(")");
            return builder.toString();
        }

        @Override
        public String firstArg(ExpressionValue argument) {
            return argument.with(this);
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
            return config + "(" + notation + ")";
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
        public String mapLiteral(Map<String, ExpressionValue> map) {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            addEntries(map, builder);
            builder.append("]");
            return builder.toString();
        }

        private void addEntries(Map<String, ExpressionValue> map, StringBuilder builder) {
            boolean first = true;
            for (Map.Entry<String, ExpressionValue> entry : map.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    builder.append(", ");
                }
                builder.append(entry.getKey());
                builder.append(": ");
                builder.append(entry.getValue().with(this));
            }
        }

        @Override
        public String firstArg(ExpressionValue argument) {
            if (argument instanceof MapLiteralValue) {
                MapLiteralValue literalValue = (MapLiteralValue) argument;
                StringBuilder builder = new StringBuilder();
                addEntries(literalValue.literal, builder);
                return builder.toString();
            } else {
                return argument.with(this);
            }
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
            return config + " " + notation;
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
