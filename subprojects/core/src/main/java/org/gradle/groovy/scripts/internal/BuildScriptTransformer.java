/*
 * Copyright 2009 the original author or authors.
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
import org.codehaus.groovy.control.CompilationUnit;
import org.gradle.api.specs.Spec;
import org.gradle.configuration.ScriptTarget;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.Transformer;
import org.gradle.internal.Factory;
import org.gradle.model.dsl.internal.transform.ModelBlockTransformer;

import java.util.Arrays;
import java.util.List;

public class BuildScriptTransformer implements Transformer, Factory<BuildScriptData> {

    private final Spec<? super Statement> filter;
    private final ScriptSource scriptSource;

    private final ImperativeStatementDetectingTransformer imperativeStatementDetectingTransformer = new ImperativeStatementDetectingTransformer();

    public BuildScriptTransformer(ScriptSource scriptSource, ScriptTarget scriptTarget) {
        final List<String> blocksToIgnore = Arrays.asList(scriptTarget.getClasspathBlockName(), InitialPassStatementTransformer.PLUGINS, InitialPassStatementTransformer.PLUGIN_MANAGEMENT);
        this.filter = new Spec<Statement>() {
            @Override
            public boolean isSatisfiedBy(Statement statement) {
                return AstUtils.detectScriptBlock(statement, blocksToIgnore) != null;
            }
        };
        this.scriptSource = scriptSource;
    }

    @Override
    public void register(CompilationUnit compilationUnit) {
        new FilteringScriptTransformer(filter).register(compilationUnit);
        new TaskDefinitionScriptTransformer().register(compilationUnit);
        new FixMainScriptTransformer().register(compilationUnit);
        new StatementLabelsScriptTransformer().register(compilationUnit);
        new ModelBlockTransformer(scriptSource.getDisplayName(), scriptSource.getResource().getLocation().getURI()).register(compilationUnit);
        imperativeStatementDetectingTransformer.register(compilationUnit);
        new CompoundAssignmentTransformer().register(compilationUnit);
    }

    @Override
    public BuildScriptData create() {
        return new BuildScriptData(imperativeStatementDetectingTransformer.isImperativeStatementDetected());
    }
}
