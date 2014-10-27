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

import org.apache.commons.io.FileUtils;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Twirl compiler uses reflection to load and invoke TwirlCompiler$
 *
 * TODO: currently this is just a dummy implementation doing a simple file copy
 * */
public class TwirlCompiler implements Compiler<TwirlCompileSpec>, Serializable {

    public WorkResult execute(TwirlCompileSpec spec) {
        try {
            Class<?> twirlCompiler = getClass().getClassLoader().loadClass("play.twirl.compiler.TwirlCompiler$");
            File sourceDirectory = (new File("app")).getCanonicalFile(); //TODO: add to spec?
            File generatedDirectory = spec.getDestinationDir();
            String formatterType = spec.getFormatterType();
            String additionalImports = spec.getAdditionalImports();

            boolean inclusiveDots = spec.isInclusiveDots();
            boolean useOldParser = spec.isUseOldParser();

            Class<?> codecClass = getClass().getClassLoader().loadClass("scala.io.Codec");
            Class<?> codecObject =  getClass().getClassLoader().loadClass("scala.io.Codec$");
            String codec = spec.getCodec();
            try {
                //TODO: reflection is slow, cache this:
                Field codeModule = codecObject.getDeclaredField("MODULE$");
                //codeModule.
                //Method codecApply = ;getMethod("apply", codec.getClass());
                Object scalaCodec = codecApply.invoke(null, "utf-8");
                Method twirlCompile = twirlCompiler.getMethod(
                        "compile",
                        File.class, //input
                        sourceDirectory.getClass(),
                        generatedDirectory.getClass(),
                        formatterType.getClass(),
                        additionalImports.getClass(),
                        codecClass,
                        boolean.class,
                        boolean.class);

                for (File inputFile: spec.getSources()) {
                    twirlCompile.invoke(null,
                            inputFile,
                            sourceDirectory,
                            generatedDirectory,
                            formatterType,
                            scalaCodec,
                            inclusiveDots,
                            useOldParser);
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }


            /**
             * TODO load compiler method via reflection and invoke for each source file with
             * parameter from spec
             *
             * */

            Iterable<File> sources = spec.getSources();
            for(File sourceFile : sources){
                FileUtils.writeStringToFile(new File(spec.getDestinationDir(), sourceFile.getName()), "compiled " + sourceFile.getName());
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new SimpleWorkResult(false);
    }

}
