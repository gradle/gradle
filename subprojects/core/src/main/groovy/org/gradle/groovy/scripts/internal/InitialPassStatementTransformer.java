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
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Factory;
import org.gradle.plugin.use.internal.PluginRequests;
import org.gradle.plugin.use.internal.PluginUseScriptBlockMetadataExtractor;

import java.util.Arrays;
import java.util.List;

public class InitialPassStatementTransformer implements StatementTransformer, Factory<PluginRequests> {

    public static final String PLUGINS = "plugins";

    private final String classpathBlockName;
    private final String pluginsBlockMessage;

    private final PluginUseScriptBlockMetadataExtractor pluginBlockMetadataExtractor;
    private boolean seenNonClasspathStatement;
    private boolean seenPluginsBlock;
    private final List<String> scriptBlockNames;

    public InitialPassStatementTransformer(String classpathBlockName, String pluginsBlockMessage,
                                           ScriptSource scriptSource, DocumentationRegistry documentationRegistry) {
        this.classpathBlockName = classpathBlockName;
        this.scriptBlockNames = Arrays.asList(classpathBlockName, PLUGINS);
        this.pluginsBlockMessage = pluginsBlockMessage;
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

                if (pluginsBlockMessage != null) {
                    failMessage = pluginBlockMetadataExtractor.formatErrorMessage(pluginsBlockMessage);
                } else {
                    seenPluginsBlock = true;
                    if (seenNonClasspathStatement) {
                        failMessage = String.format(
                                pluginBlockMetadataExtractor.formatErrorMessage("only %s {} and other %s {} script blocks are allowed before %s {} blocks, no other statements are allowed"),
                                classpathBlockName, PLUGINS, PLUGINS
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
            } else {
                if (seenPluginsBlock) {
                    String message = String.format(
                            pluginBlockMetadataExtractor.formatErrorMessage("all %s {} blocks must appear before any %s {} blocks in the script"),
                            classpathBlockName, PLUGINS
                    );
                    sourceUnit.getErrorCollector().addError(
                            new SyntaxException(message, statement.getLineNumber(), statement.getColumnNumber()),
                            sourceUnit
                    );
                }
                return statement; // don't transform classpathBlockName
            }
        }
    }

    @Override
    public PluginRequests create() {
        return pluginBlockMetadataExtractor.getRequests();
    }

}
