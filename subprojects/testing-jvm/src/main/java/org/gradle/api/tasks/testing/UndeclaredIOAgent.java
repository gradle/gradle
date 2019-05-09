/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.tasks.testing;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * The Java agent.
 */
public class UndeclaredIOAgent {
    /**
     * The entry point.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.setProperty("undeclared.io.agent.file", agentArgs);
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .ignore(not(nameStartsWith("java.io")))
            .type(named(File.class.getName()))
            .transform(new FileTransformer())
            .with(RedefinitionStrategy.RETRANSFORMATION)
            .installOn(inst);
    }

    private static class FileTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(
            DynamicType.Builder<?> builder,
            TypeDescription typeDescription,
            ClassLoader classLoader,
            JavaModule module
        ) {
            return builder.visit(
                Advice.to(CaptureFile.class)
                    .on(isMethod())
            );
        }
    }

    /**
     * The inlined input capturing code.
     */
    public static class CaptureFile {

        @Advice.OnMethodEnter
        public static void entry(@Advice.This(optional = true) File file) {
            if (System.getProperty("capturing.io") != null || file == null) {
                return;
            }
            System.setProperty("capturing.io", "true");
            try {
                String outputFile = System.getProperty("undeclared.io.agent.file");
                PrintStream out = new PrintStream(new FileOutputStream(outputFile, true));
                out.println(file);
                out.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            System.clearProperty("capturing.io");
        }
    }
}
