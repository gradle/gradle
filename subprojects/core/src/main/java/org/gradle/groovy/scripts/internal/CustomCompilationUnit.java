/*
 * Copyright 2021 the original author or authors.
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

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.security.CodeSource;
import java.util.List;
import java.util.Map;

class CustomCompilationUnit extends CompilationUnit {

    public CustomCompilationUnit(CompilerConfiguration compilerConfiguration, CodeSource codeSource, GroovyClassLoader groovyClassLoader, Map<String, List<String>> simpleNameToFQN) {
        super(compilerConfiguration, codeSource, groovyClassLoader);
        this.resolveVisitor = new GradleResolveVisitor(this, simpleNameToFQN);
    }

}
