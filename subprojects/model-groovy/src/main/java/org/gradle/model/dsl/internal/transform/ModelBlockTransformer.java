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

package org.gradle.model.dsl.internal.transform;

import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.groovy.scripts.internal.AbstractScriptTransformer;
import org.gradle.groovy.scripts.internal.AstUtils;
import org.gradle.groovy.scripts.internal.ScriptBlock;

import java.util.Collections;
import java.util.List;

public class ModelBlockTransformer extends AbstractScriptTransformer {

    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    public String getId() {
        return "model";
    }

    private static final List<String> SCRIPT_BLOCK_NAMES = Collections.singletonList("model");

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        RuleClosureVisitor ruleVisitor = new RuleClosureVisitor(source);
        List<Statement> statements = source.getAST().getStatementBlock().getStatements();
        for (Statement statement : statements) {
            ScriptBlock scriptBlock = AstUtils.detectScriptBlock(statement, SCRIPT_BLOCK_NAMES);
            if (scriptBlock != null) {
                visitModelBlock(scriptBlock, ruleVisitor);
            }
        }
    }

    private void visitModelBlock(ScriptBlock scriptBlock, GroovyCodeVisitor ruleVisitor) {
        Statement code = scriptBlock.getClosureExpression().getCode();
        for (Statement statement : AstUtils.unpack(code)) {
            ScriptBlock rule = AstUtils.detectScriptBlock(statement);
            if (rule != null) {
                rule.getClosureExpression().visit(ruleVisitor);
            }
        }
    }

}
