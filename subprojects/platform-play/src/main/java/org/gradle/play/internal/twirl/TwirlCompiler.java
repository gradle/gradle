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
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Twirl compiler uses reflection to load and invoke TwirlCompiler$
 */
public class TwirlCompiler implements Compiler<TwirlCompileSpec>, Serializable {

    private TwirlCompilerVersionedInvocationSpecBuilder invocationSpecBuilder;

    public TwirlCompiler(TwirlCompilerVersionedInvocationSpecBuilder invocationSpecBuilder){
        this.invocationSpecBuilder = invocationSpecBuilder;
    };

    public WorkResult execute(TwirlCompileSpec spec) {
        ArrayList<File> outputFiles = Lists.newArrayList();
        try {
            VersionedInvocationSpec invocationSpec = invocationSpecBuilder.build(spec);
            Function<Object[], Object> compile = ScalaUtil.scalaObjectFunction(getClass().getClassLoader(),
                    invocationSpec.getVersion().getCompilerClassname(),
                    "compile",
                    invocationSpec.getParameterTypes());


            Iterable<File> sources = spec.getSources();
            for (File sourceFile : sources) {
                Object[] parameters  = invocationSpec.getParameters(sourceFile.getCanonicalFile());
                Object result = compile.apply(parameters);
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


}
