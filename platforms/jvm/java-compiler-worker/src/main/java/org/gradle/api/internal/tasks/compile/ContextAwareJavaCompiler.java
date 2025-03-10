/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.util.Context;

import javax.annotation.Nullable;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.Writer;

/**
 * This interface extends the standard {@link JavaCompiler} interface with {@link com.sun.tools.javac.api.JavacTool}'s internal method
 * {@link com.sun.tools.javac.api.JavacTool#getTask(Writer, JavaFileManager, DiagnosticListener, Iterable, Iterable, Iterable, Context)}, which has an additional {@link Context} parameter.
 */
public interface ContextAwareJavaCompiler extends JavaCompiler {

    JavacTask getTask(
        @Nullable Writer out,
        JavaFileManager fileManager,
        DiagnosticListener<? super JavaFileObject> diagnosticListener,
        Iterable<String> options,
        Iterable<String> classes,
        Iterable<? extends JavaFileObject> compilationUnits,
        Context context
    );

}
