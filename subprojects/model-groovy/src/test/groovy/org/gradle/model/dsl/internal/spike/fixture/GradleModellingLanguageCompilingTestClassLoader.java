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

package org.gradle.model.dsl.internal.spike.fixture;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.gradle.model.dsl.internal.spike.GradleModellingLanguageTransformer;

import java.security.CodeSource;

class GradleModellingLanguageCompilingTestClassLoader extends GroovyClassLoader {

    protected CompilationUnit createCompilationUnit(CompilerConfiguration compilerConfiguration,
                                                    CodeSource codeSource) {
        CompilationUnit compilationUnit = new CompilationUnit(compilerConfiguration, codeSource, this);

        compilationUnit.addPhaseOperation(new GradleModellingLanguageTransformer(), Phases.CANONICALIZATION);
        return compilationUnit;
    }
}