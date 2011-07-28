/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.cpp.compiler

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.IdentityFileResolver

import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleBuilder

import org.gradle.plugins.cpp.model.Compiler

public class ExternalProcessCompiler extends ExecHandleBuilder implements Compiler {

    public ExternalProcessCompiler(FileResolver fileResolver) {
        super(fileResolver)
    }

    public ExternalProcessCompiler() {
        super(new IdentityFileResolver())
    }
    
    void compile() {
        ExecHandle execHandle = build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!isIgnoreExitValue()) {
            execResult.assertNormalExitValue();
        }
    }

}