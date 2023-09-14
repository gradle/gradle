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

package org.gradle.groovy.scripts.internal;

import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.configuration.ScriptTarget;
import org.gradle.plugin.use.internal.PluginUseScriptBlockMetadataCompiler;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Removes all statements from the given script except the top-level script blocks such as
 * {@code buildscript}, {@code plugins} and {@code pluginManagement}.
 */
public class InitialPassStatementTransformer implements StatementTransformer {

    public static final String PLUGINS = "plugins";
    public static final String PLUGIN_MANAGEMENT = "pluginManagement";

    private final ScriptTarget scriptTarget;
    private final List<String> scriptBlockNames;
    private final DocumentationRegistry documentationRegistry;
    private final PluginUseScriptBlockMetadataCompiler pluginBlockMetadataCompiler;

    private boolean seenNonClasspathStatement;
    private boolean seenPluginsBlock;
    private boolean seenPluginManagementBlock;
    private boolean seenClasspathBlock;

    public InitialPassStatementTransformer(ScriptTarget scriptTarget,
                                           DocumentationRegistry documentationRegistry) {
        this.scriptTarget = scriptTarget;
        this.scriptBlockNames = Arrays.asList(scriptTarget.getClasspathBlockName(), PLUGINS, PLUGIN_MANAGEMENT);
        this.documentationRegistry = documentationRegistry;
        this.pluginBlockMetadataCompiler = new PluginUseScriptBlockMetadataCompiler(documentationRegistry, scriptTarget.getPluginsBlockPermits());
    }

    @Override
    public Statement transform(SourceUnit sourceUnit, Statement statement) {
        ScriptBlock scriptBlock = AstUtils.detectScriptBlock(statement, scriptBlockNames);
        if (scriptBlock == null) {
            seenNonClasspathStatement = true;
            return null;
        }

        if (scriptBlock.getName().equals(PLUGINS)) {
            return transformPluginsBlock(scriptBlock, sourceUnit, statement);
        }

        if (scriptBlock.getName().equals(PLUGIN_MANAGEMENT)) {
            return transformPluginManagementBlock(sourceUnit, statement);
        }

        if (seenPluginsBlock) {
            String message = String.format(
                pluginBlockMetadataCompiler.formatErrorMessage("all %s {} blocks must appear before any %s {} blocks in the script"),
                scriptTarget.getClasspathBlockName(), PLUGINS
            );
            addSyntaxError(message, sourceUnit, statement);
        }
        seenClasspathBlock = true;
        return statement;
    }

    private Statement transformPluginsBlock(ScriptBlock scriptBlock, SourceUnit sourceUnit, Statement statement) {
        String failMessage = null;

        if (!scriptTarget.getSupportsPluginsBlock()) {
            failMessage = pluginBlockMetadataCompiler.formatErrorMessage("Only Project and Settings build scripts can contain plugins {} blocks");
        } else {
            seenPluginsBlock = true;

            addLineNumberToMethodCall(scriptBlock);

            if (seenNonClasspathStatement) {
                failMessage = String.format(
                    pluginBlockMetadataCompiler.formatErrorMessage("only %s {}, %s {} and other %s {} script blocks are allowed before %s {} blocks, no other statements are allowed"),
                    scriptTarget.getClasspathBlockName(), PLUGIN_MANAGEMENT, PLUGINS, PLUGINS
                );
            } else {
                pluginBlockMetadataCompiler.compile(sourceUnit, scriptBlock);
            }
        }

        if (failMessage != null) {
            addSyntaxError(failMessage, sourceUnit, statement);
        }

        return statement;
    }

    // Add the block line-number as an argument to call `plugins(int lineNumber, Closure pluginsBlock)`
    private void addLineNumberToMethodCall(ScriptBlock scriptBlock) {
        ConstantExpression lineNumberExpression = new ConstantExpression(scriptBlock.getClosureExpression().getLineNumber(), true);
        scriptBlock.getMethodCall().setArguments(new ArgumentListExpression(lineNumberExpression, scriptBlock.getClosureExpression()));
    }

    private Statement transformPluginManagementBlock(SourceUnit sourceUnit, Statement statement) {
        String failureMessage = failureMessageForPluginManagementBlock();
        if (failureMessage != null) {
            addSyntaxError(makePluginManagementError(failureMessage), sourceUnit, statement);
        }
        seenPluginManagementBlock = true;
        return statement;
    }

    private void addSyntaxError(String errorMessage, SourceUnit sourceUnit, Statement statement) {
        sourceUnit.getErrorCollector().addError(
            new SyntaxException(errorMessage, statement.getLineNumber(), statement.getColumnNumber()),
            sourceUnit
        );
    }

    @Nullable
    private String failureMessageForPluginManagementBlock() {
        if (!scriptTarget.getSupportsPluginManagementBlock()) {
            return "Only Settings scripts can contain a pluginManagement {} block.";
        }
        if (seenClasspathBlock || seenNonClasspathStatement || seenPluginsBlock) {
            return String.format("The %s {} block must appear before any other statements in the script.", PLUGIN_MANAGEMENT);
        }
        if (seenPluginManagementBlock) {
            return String.format("At most, one %s {} block may appear in the script.", PLUGIN_MANAGEMENT);
        }
        return null;
    }

    private String makePluginManagementError(String failureMessage) {
        return String.format(
            "%s%n%n%s%n%n",
            failureMessage,
            documentationRegistry.getDocumentationRecommendationFor("information on the pluginManagement {} block", "plugins", "sec:plugin_management"));
    }

}
