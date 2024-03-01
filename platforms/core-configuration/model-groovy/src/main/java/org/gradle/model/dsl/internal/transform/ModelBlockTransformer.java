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

import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;
import org.gradle.groovy.scripts.internal.AbstractScriptTransformer;
import org.gradle.groovy.scripts.internal.AstUtils;
import org.gradle.groovy.scripts.internal.ScriptBlock;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public class ModelBlockTransformer extends AbstractScriptTransformer {
    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    public static final String MODEL = "model";
    private static final List<String> SCRIPT_BLOCK_NAMES = Collections.singletonList(MODEL);

    public static final String NON_LITERAL_CLOSURE_TO_TOP_LEVEL_MODEL_MESSAGE = "The top level model() method can only be called with a literal closure argument";

    private final String scriptSourceDescription;
    private @Nullable final URI location;

    public ModelBlockTransformer(String scriptSourceDescription, @Nullable URI location) {
        this.scriptSourceDescription = scriptSourceDescription;
        this.location = location;
    }

    /*
        TODO change this so that we extract all the information at compile time.

        At the moment we use the transform to:

        1. validate/restrict the syntax
        2. transform rules into something more robust (e.g. foo.bar.baz {} into configure("foo.bar.baz", {})) - no dynamic propertyMissing() nonsense
        3. hoist out input references (i.e. $()) into an annotation on rule closure classes to make available

        This means we actually have to execute the code block in order to find the rule information within.
        This is also problematic because it means we have to serialize this information into some form that fits into annotations.

        Later, we will extract all the “up-front” information we need to know during compile time.
        This will mean that we only need to execute the rules themselves, and not any code to actually register the rules.
     */

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        List<Statement> statements = source.getAST().getStatementBlock().getStatements();
        for (Statement statement : statements) {
            ScriptBlock scriptBlock = AstUtils.detectScriptBlock(statement, SCRIPT_BLOCK_NAMES);
            if (scriptBlock == null) {
                // Look for model(«») (i.e. call to model with anything other than non literal closure)
                MethodCallExpression methodCall = AstUtils.extractBareMethodCall(statement);
                if (methodCall == null) {
                    continue;
                }

                String methodName = AstUtils.extractConstantMethodName(methodCall);
                if (methodName == null) {
                    continue;
                }

                if (methodName.equals(MODEL)) {
                    source.getErrorCollector().addError(
                            new SyntaxException(NON_LITERAL_CLOSURE_TO_TOP_LEVEL_MODEL_MESSAGE, statement.getLineNumber(), statement.getColumnNumber()),
                            source
                    );
                }
            } else {
                RuleVisitor ruleVisitor = new RuleVisitor(source, scriptSourceDescription, location);
                RulesVisitor rulesVisitor = new RulesVisitor(source, ruleVisitor);
                scriptBlock.getClosureExpression().getCode().visit(rulesVisitor);
            }
        }
    }

}
