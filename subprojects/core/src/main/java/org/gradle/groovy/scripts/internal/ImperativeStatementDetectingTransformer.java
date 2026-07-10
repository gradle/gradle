/*
 * Copyright 2015 the original author or authors.
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

import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.model.dsl.internal.transform.ModelBlockTransformer;

import java.util.List;

public class ImperativeStatementDetectingTransformer extends AbstractScriptTransformer {
    private boolean imperativeStatementDetected;

    @Override
    public void register(CompilationUnit compilationUnit) {
        super.register(compilationUnit);
    }

    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    public boolean isImperativeStatementDetected() {
        return imperativeStatementDetected;
    }

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        BlockStatement statementBlock = source.getAST().getStatementBlock();
        List<Statement> statements = statementBlock.getStatements();
        for (Statement statement : statements) {
            if (!AstUtils.mayHaveAnEffect(statement)) {
                continue;
            }
            ScriptBlock scriptBlock = AstUtils.detectScriptBlock(statement);
            if (scriptBlock != null && scriptBlock.getName().equals(ModelBlockTransformer.MODEL)) {
                continue;
            }
            imperativeStatementDetected = true;
            break;
        }
    }
}
