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
import org.gradle.play.internal.ScalaUtil;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Twirl compiler uses reflection to load and invoke TwirlCompiler$
 */
public class TwirlCompiler implements Compiler<TwirlCompileSpec>, Serializable {

    public WorkResult execute(TwirlCompileSpec spec) {
        ArrayList<File> outputFiles = Lists.newArrayList();
        try {
            VersionedTwirlCompileSpec versionedTwirlCompileSpec = createVersionedSpec(spec);
            Function<Object[], Object> compile = ScalaUtil.scalaObjectFunction(getClass().getClassLoader(),
                    spec.getCompilerClassName(),
                    "compile",
                    versionedTwirlCompileSpec.getParameterTypes());


            Iterable<File> sources = spec.getSources();
            Object[] prefilledCompileParameters = versionedTwirlCompileSpec.getParameters();
            for (File sourceFile : sources) {
                prefilledCompileParameters[0] = sourceFile.getCanonicalFile();
                Object result = compile.apply(prefilledCompileParameters);
                Method resultIsDefined = result.getClass().getMethod("isDefined");
                if ((Boolean) resultIsDefined.invoke(result)) {
                    File createdFile = (File) result.getClass().getMethod("get").invoke(result);
                    outputFiles.add(createdFile);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error invoking template compiler", e);
        }

        return new TwirlCompilerWorkResult(outputFiles);
    }

    private VersionedTwirlCompileSpec createVersionedSpec(TwirlCompileSpec spec) throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException {
        if (spec.getCompilerClassName().equals("play.templates.ScalaTemplateCompiler")) {
            return new VersionedTwirlCompileSpec(new Class<?>[]{
                    File.class,
                    spec.getSourceDirectory().getClass(),
                    spec.getDestinationDir().getClass(),
                    spec.getFormatterType().getClass(),
                    spec.getAdditionalImports().getClass()},

                    new Object[]{
                            null,
                            spec.getSourceDirectory(),
                            spec.getDestinationDir(),
                            spec.getFormatterType(),
                            spec.getAdditionalImports()
                    });
        } else {
            ClassLoader cl = getClass().getClassLoader();
            Class<?> codecClass = cl.loadClass("scala.io.Codec");

            Function<Object[], Object> ioCodec = ScalaUtil.scalaObjectFunction(cl,
                    "scala.io.Codec",
                    "apply",
                    new Class<?>[]{
                            String.class
                    });
            Object scalaCodec = ioCodec.apply(new Object[]{
                    spec.getCodec()
            });
            return new VersionedTwirlCompileSpec(new Class<?>[]{
                    File.class,
                    spec.getSourceDirectory().getClass(),
                    spec.getDestinationDir().getClass(),
                    spec.getFormatterType().getClass(),
                    spec.getAdditionalImports().getClass(),
                    codecClass,
                    boolean.class,
                    boolean.class},

                    new Object[]{
                            null,
                            spec.getSourceDirectory(),
                            spec.getDestinationDir(),
                            spec.getFormatterType(),
                            spec.getAdditionalImports(),
                            scalaCodec,
                            spec.isInclusiveDots(),
                            spec.isUseOldParser()
                    });
        }
    }

    private class VersionedTwirlCompileSpec {
        private Class<?>[] parameterTypes;
        private Object[] parameters;

        public VersionedTwirlCompileSpec(Class<?>[] parameterTypes, Object[] parameters) {
            this.parameterTypes = parameterTypes;
            this.parameters = parameters;
        }

        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        public Object[] getParameters() {
            return parameters;
        }
    }
}
