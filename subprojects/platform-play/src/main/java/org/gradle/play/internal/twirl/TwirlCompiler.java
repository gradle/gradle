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

package org.gradle.play.internal.twirl;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Twirl compiler uses reflection to load and invoke TwirlCompiler$
 *
 */
public class TwirlCompiler implements Compiler<TwirlCompileSpec>, Serializable {

    /**
     * Invokes a method on a scala object
     */
    private static Function<Object[], Object> scalaObjectFunction(ClassLoader classLoader, String objectName, String methodName, Class<?>[] typeParameters) throws ClassNotFoundException, NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Class<?> baseClass = classLoader.loadClass(objectName+"$");
        final Field scalaObject = baseClass.getDeclaredField("MODULE$");

        final Method scalaObjectMethod = scalaObject.getType().getMethod(methodName, typeParameters);

        Function<Object[], Object> function = new Function<Object[], Object>() {
            public Object apply(Object[] args) {
                try {
                    return scalaObjectMethod.invoke(scalaObject.get(null), args);
                } catch (Exception e) {
                    throw new RuntimeException(e.getCause());
                }
            }

            public boolean equals(Object object) {
                return false;
            }

            public int hashCode() {
                return scalaObjectMethod.hashCode() + scalaObject.hashCode(); //we had to have a hashCode here because Function requires an equals. Maybe using Function is not the best option.
            }
        };
        return function;
    }

    public WorkResult execute(TwirlCompileSpec spec) {
        ArrayList<File> outputFiles = Lists.newArrayList();
        try {
            File sourceDirectory = spec.getSourceDirectory().getCanonicalFile();
            File generatedDirectory = spec.getDestinationDir();
            String formatterType = spec.getFormatterType();
            String additionalImports = spec.getAdditionalImports();
            boolean inclusiveDots = spec.isInclusiveDots();
            boolean useOldParser = spec.isUseOldParser();
            String codec = spec.getCodec();
            ClassLoader cl = getClass().getClassLoader();
            Class<?> codecClass = cl.loadClass("scala.io.Codec");

            Function<Object[], Object> ioCodec = scalaObjectFunction(cl,
                    "scala.io.Codec",
                    "apply",
                    new Class<?>[]{
                            String.class
                    });
            Object scalaCodec = ioCodec.apply(new Object[]{
                    codec
            });

            Function<Object[], Object> compile = scalaObjectFunction(cl,
                    "play.twirl.compiler.TwirlCompiler",
                    "compile",
                    new Class<?>[]{
                            File.class,
                            sourceDirectory.getClass(),
                            generatedDirectory.getClass(),
                            formatterType.getClass(),
                            additionalImports.getClass(),
                            codecClass,
                            boolean.class,
                            boolean.class});


            Iterable<File> sources = spec.getSources();
            for (File sourceFile : sources) {
                Object result = compile.apply(new Object[]{
                        sourceFile.getCanonicalFile(),
                        sourceDirectory,
                        generatedDirectory,
                        formatterType,
                        additionalImports,
                        scalaCodec,
                        inclusiveDots,
                        useOldParser
                });
                Method resultIsDefined = result.getClass().getMethod("isDefined");
                if ((Boolean) resultIsDefined.invoke(result)) {
                    File createdFile = (File) result.getClass().getMethod("get").invoke(result);
                    outputFiles.add(createdFile);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getCause());
        }

        return new TwirlCompilerWorkResult(outputFiles);
    }

}
