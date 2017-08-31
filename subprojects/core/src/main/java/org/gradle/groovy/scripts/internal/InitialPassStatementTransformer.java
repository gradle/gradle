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

import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.configuration.ScriptTarget;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Factory;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.internal.PluginUseScriptBlockMetadataExtractor;

import java.util.Arrays;
import java.util.List;

public class InitialPassStatementTransformer implements StatementTransformer, Factory<PluginRequests> {

    public static final String PLUGINS = "plugins";
    public static final String PLUGIN_MANAGEMENT = "pluginManagement";

    private final ScriptTarget scriptTarget;
    private final List<String> scriptBlockNames;
    private final DocumentationRegistry documentationRegistry;
    private final PluginUseScriptBlockMetadataExtractor pluginBlockMetadataExtractor;

    private boolean seenNonClasspathStatement;
    private boolean seenPluginsBlock;
    private boolean seenPluginManagementBlock;
    private boolean seenClasspathBlock;

    public InitialPassStatementTransformer(ScriptSource scriptSource, ScriptTarget scriptTarget, DocumentationRegistry documentationRegistry) {
        this.scriptTarget = scriptTarget;
        this.scriptBlockNames = Arrays.asList(scriptTarget.getClasspathBlockName(), PLUGINS, PLUGIN_MANAGEMENT);
        this.documentationRegistry = documentationRegistry;
        this.pluginBlockMetadataExtractor = new PluginUseScriptBlockMetadataExtractor(scriptSource, documentationRegistry);
    }

    public Statement transform(SourceUnit sourceUnit, Statement statement) {
        ScriptBlock scriptBlock = AstUtils.detectScriptBlock(statement, scriptBlockNames);
        if (scriptBlock == null) {
            seenNonClasspathStatement = true;
            return null;
        } else {
            if (scriptBlock.getName().equals(PLUGINS)) {
                String failMessage = null;

                if (!scriptTarget.getSupportsPluginsBlock()) {
                    failMessage = pluginBlockMetadataExtractor.formatErrorMessage("Only Project build scripts can contain plugins {} blocks");
                } else {
                    seenPluginsBlock = true;
                    if (seenNonClasspathStatement) {
                        failMessage = String.format(
                            pluginBlockMetadataExtractor.formatErrorMessage("only %s {} and other %s {} script blocks are allowed before %s {} blocks, no other statements are allowed"),
                            scriptTarget.getClasspathBlockName(), PLUGINS, PLUGINS
                        );
                    } else {
                        pluginBlockMetadataExtractor.extract(sourceUnit, scriptBlock);
                    }
                }

                if (failMessage != null) {
                    sourceUnit.getErrorCollector().addError(
                        new SyntaxException(failMessage, statement.getLineNumber(), statement.getColumnNumber()),
                        sourceUnit
                    );
                }

                return null;
            } else if (scriptBlock.getName().equals(PLUGIN_MANAGEMENT)) {
                String failureMessage = null;
                if (!scriptTarget.getSupportsPluginManagementBlock()) {
                    failureMessage = "Only Settings scripts can contain a pluginManagement {} block.";
                } else if (seenClasspathBlock || seenNonClasspathStatement || seenPluginsBlock) {
                    failureMessage = String.format("The %s {} block must appear before any other statements in the script.", PLUGIN_MANAGEMENT);
                } else if (seenPluginManagementBlock) {
                    failureMessage = String.format("At most, one %s {} block may appear in the script.", PLUGIN_MANAGEMENT);
                }
                if (failureMessage != null) {
                    sourceUnit.getErrorCollector().addError(
                        new SyntaxException(makePluginManagementError(failureMessage), statement.getLineNumber(), statement.getColumnNumber()), sourceUnit);
                }
                seenPluginManagementBlock = true;
                return statement;
            } else {
                if (seenPluginsBlock) {
                    String message = String.format(
                            pluginBlockMetadataExtractor.formatErrorMessage("all %s {} blocks must appear before any %s {} blocks in the script"),
                            scriptTarget.getClasspathBlockName(), PLUGINS
                    );
                    sourceUnit.getErrorCollector().addError(
                            new SyntaxException(message, statement.getLineNumber(), statement.getColumnNumber()),
                            sourceUnit
                    );
                }
                seenClasspathBlock = true;
                return statement;
            }
        }
    }

    private String makePluginManagementError(String failureMessage) {
        return String.format(
            "%s%n%nSee %s for information on the pluginManagement {} block%n%n",
            failureMessage,
            documentationRegistry.getDocumentationFor("plugins", "sec:plugin_management"));
    }

    @Override
    public PluginRequests create() {
        return pluginBlockMetadataExtractor.getPluginRequests();
    }

}
