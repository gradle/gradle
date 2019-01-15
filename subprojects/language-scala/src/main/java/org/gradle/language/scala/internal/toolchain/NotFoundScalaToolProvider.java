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

package org.gradle.language.scala.internal.toolchain;

import org.gradle.api.GradleException;
import org.gradle.internal.logging.text.DiagnosticsVisitor;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.platform.base.internal.toolchain.ToolProvider;

public class NotFoundScalaToolProvider implements ToolProvider {
    private Exception exception;

    public NotFoundScalaToolProvider(Exception moduleVersionNotFoundException) {
        this.exception = moduleVersionNotFoundException;
    }

    @Override
    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(Class<T> spec) {
        throw failure();
    }

    @Override
    public <T> T get(Class<T> toolType) {
        throw failure();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    private RuntimeException failure() {
        TreeFormatter formatter = new TreeFormatter();
        this.explain(formatter);
        return new GradleException(formatter.toString(), exception);
    }

    @Override
    public void explain(DiagnosticsVisitor visitor) {
        visitor.node("Cannot provide Scala Compiler");
        visitor.startChildren();
        visitor.node(exception.getCause().getMessage());
        visitor.endChildren();
    }
}
