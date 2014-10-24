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

/**
 * Twirl compiler uses reflection to load and invoke TwirlCompiler$
 *
 * TODO: currently this is just a dummy implementation doing a simple file copy
 * */
public class TwirlCompiler implements Compiler<TwirlCompileSpec>, Serializable {

    public WorkResult execute(TwirlCompileSpec spec) {
        try {
            Class<?> twirlCompiler = getClass().getClassLoader().loadClass("play.twirl.compiler.TwirlCompiler$");

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
