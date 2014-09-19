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

import org.codehaus.groovy.control.CompilationUnit;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.Transformer;
import org.gradle.model.dsl.internal.transform.ModelBlockTransformer;

public class BuildScriptTransformer implements Transformer {

    private final String id;
    private final Transformer extractionTransformer;
    private final ScriptSource scriptSource;

    public BuildScriptTransformer(String id, Transformer extractionTransformer, ScriptSource scriptSource) {
        this.id = id;
        this.extractionTransformer = extractionTransformer;
        this.scriptSource = scriptSource;
    }

    public String getId() {
        return id;
    }

    public void register(CompilationUnit compilationUnit) {
        extractionTransformer.register(compilationUnit);
        new TaskDefinitionScriptTransformer().register(compilationUnit);
        new FixMainScriptTransformer().register(compilationUnit); // TODO - remove this
        new StatementLabelsScriptTransformer().register(compilationUnit);
        new ScriptSourceDescriptionTransformer(scriptSource.getDisplayName()).register(compilationUnit);
        new ModelBlockTransformer().register(compilationUnit);
    }
}
