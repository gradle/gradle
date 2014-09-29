/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.dsl.internal.spike;

import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.groovy.scripts.internal.AstUtils;
import org.gradle.groovy.scripts.internal.ScriptBlock;

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class GradleModellingLanguageTransformer extends CompilationUnit.SourceUnitOperation {

    private static final String MODEL = "model";
    private static final List<String> SCRIPT_BLOCK_NAMES = Collections.singletonList(MODEL);

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        ModelRegistryDslHelperStatementGenerator statementGenerator = new ModelRegistryDslHelperStatementGenerator();
        BlockStatement rootStatementBlock = source.getAST().getStatementBlock();
        ListIterator<Statement> statementsIterator = rootStatementBlock.getStatements().listIterator();
        while (statementsIterator.hasNext()) {
            Statement statement = statementsIterator.next();
            ScriptBlock scriptBlock = AstUtils.detectScriptBlock(statement, SCRIPT_BLOCK_NAMES);
            if (scriptBlock != null) {
                statementsIterator.remove();
                ScopeVisitor scopeVisitor = new ScopeVisitor(source, statementGenerator);
                scriptBlock.getClosureExpression().getCode().visit(scopeVisitor);
            }
        }
        rootStatementBlock.addStatements(statementGenerator.getGeneratedStatements());
    }
}
