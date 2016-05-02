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

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;

/**
 * Fixes problem where main { } inside a closure is resolved as a call to static method main(). Does this by removing
 * the static method.
 */
public class FixMainScriptTransformer extends AbstractScriptTransformer {
    @Override
    protected int getPhase() {
        return Phases.CONVERSION;
    }

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        ClassNode scriptClass = AstUtils.getScriptClass(source);
        if (scriptClass == null) {
            return;
        }
        for (MethodNode methodNode : scriptClass.getMethods()) {
            if (methodNode.getName().equals("main")) {
                AstUtils.removeMethod(scriptClass, methodNode);
                break;
            }
        }
    }
}
