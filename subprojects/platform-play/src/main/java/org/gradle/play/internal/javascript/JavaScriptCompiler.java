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

package org.gradle.play.internal.javascript;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.plugins.javascript.base.SourceTransformationException;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("rawtypes")
public class JavaScriptCompiler implements Compiler<JavaScriptCompileSpec>, Serializable {
    private Class sourceFileClass;
    private Class compilerOptionsClass;
    private Class compilationLevelClass;
    private Class compilerClass;

    @Override
    public WorkResult execute(JavaScriptCompileSpec spec) {
        JavaScriptCompileDestinationCalculator destinationCalculator = new JavaScriptCompileDestinationCalculator(spec.getDestinationDir());
        List<String> allErrors = Lists.newArrayList();

        for (RelativeFile sourceFile : spec.getSources()) {
            allErrors.addAll(compile(sourceFile, spec, destinationCalculator));
        }

        if (allErrors.isEmpty()) {
            return new SimpleWorkResult(true);
        } else {
            throw new SourceTransformationException(String.format("Minification failed with the following errors:\n\t%s", StringUtils.join(allErrors, "\n\t")), null);
        }
    }

    @SuppressWarnings("unchecked")
    List<String> compile(RelativeFile javascriptFile, JavaScriptCompileSpec spec, JavaScriptCompileDestinationCalculator destinationCalculator) {
        List<String> errors = Lists.newArrayList();
        try {
            loadCompilerClasses(getClass().getClassLoader());

            Method fromCodeMethod = sourceFileClass.getMethod("fromCode", String.class, String.class);
            Object extern = fromCodeMethod.invoke(null, "/dev/null", "");

            Method fromFileMethod = sourceFileClass.getMethod("fromFile", File.class);
            Object sourceFile = fromFileMethod.invoke(null, javascriptFile.getFile());

            Constructor compilerOptionsConstructor = compilerOptionsClass.getConstructor();
            Object compilerOptions = compilerOptionsConstructor.newInstance();

            Object simpleLevel = Enum.valueOf(compilationLevelClass, "SIMPLE_OPTIMIZATIONS");
            Method setOptionsForCompilationLevelMethod = compilationLevelClass.getMethod("setOptionsForCompilationLevel", compilerOptionsClass);
            setOptionsForCompilationLevelMethod.invoke(simpleLevel, compilerOptions);

            Constructor compilerConstructor = compilerClass.getConstructor(PrintStream.class);
            Object compiler = compilerConstructor.newInstance(getDummyPrintStream());

            Method compileMethod = compilerClass.getMethod("compile", sourceFileClass, sourceFileClass, compilerOptionsClass);
            Object result = compileMethod.invoke(compiler, extern, sourceFile, compilerOptions);

            Field jsErrorsField = result.getClass().getField("errors");
            Object[] jsErrors = (Object[]) jsErrorsField.get(result);

            if (jsErrors.length == 0) {
                Method toSourceMethod = compilerClass.getMethod("toSource");
                String compiledSource = (String) toSourceMethod.invoke(compiler);
                GFileUtils.writeFile(compiledSource, destinationCalculator.transform(javascriptFile));
            } else {
                for (Object error : jsErrors) {
                    errors.add(error.toString());
                }
            }

            return errors;
        } catch (Exception e) {
           throw new RuntimeException("Error invoking the Play javascript compiler.", e);
        }
    }

    private void loadCompilerClasses(ClassLoader cl) throws ClassNotFoundException {
        if (sourceFileClass == null) {
            sourceFileClass = cl.loadClass("com.google.javascript.jscomp.SourceFile");
        }
        if (compilerOptionsClass == null) {
            compilerOptionsClass = cl.loadClass("com.google.javascript.jscomp.CompilerOptions");
        }
        if (compilationLevelClass == null) {
            compilationLevelClass = cl.loadClass("com.google.javascript.jscomp.CompilationLevel");
        }
        if (compilerClass == null) {
            compilerClass = cl.loadClass("com.google.javascript.jscomp.Compiler");
        }
    }

    private PrintStream getDummyPrintStream() {
        OutputStream os = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                // do nothing
            }
        };
        return new PrintStream(os);
    }
}
