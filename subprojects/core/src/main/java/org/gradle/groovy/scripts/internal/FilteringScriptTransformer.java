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

import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.specs.Spec;

import java.util.ListIterator;

public class FilteringScriptTransformer extends AbstractScriptTransformer {

    private final Spec<? super Statement> spec;

    public FilteringScriptTransformer(Spec<? super Statement> spec) {
        this.spec = spec;
    }

    @Override
    protected int getPhase() {
        return Phases.CONVERSION;
    }

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        ListIterator<Statement> iterator = source.getAST().getStatementBlock().getStatements().listIterator();
        while (iterator.hasNext()) {
            if (spec.isSatisfiedBy(iterator.next())) {
                iterator.remove();
            }
        }
    }
}
