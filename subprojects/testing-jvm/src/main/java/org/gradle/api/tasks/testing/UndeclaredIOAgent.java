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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;

public class UndeclaredIOAgent {
    private static PrintStream out;

    public static void premain(String agentArgs, Instrumentation inst) {
        try {
            out = new PrintStream(new File(agentArgs));
            new AgentBuilder.Default()/*
                .type(ElementMatchers.any())
                .transform(new CaptureIOTransformer())
                .installOn(inst)*/;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
/*
    private static class CaptureIOTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(
            DynamicType.Builder<?> builder,
            TypeDescription typeDescription,
            ClassLoader classLoader,
            JavaModule module) {
            return builder.visit(Advice.to(CaptureIOWhatever.class).on(ElementMatchers.isMethod()));
        }
    }

    private static class CaptureIOWhatever {

        @Advice.OnMethodEnter(inline = false)
        public static void monitorStart(@Advice.Origin("#t") String className,
                                        @Advice.Origin("#m") String methodName,
                                        @Advice.AllArguments Object[] args) {
            if ("UndeclaredInputReader".equals(className) && "read".equals(methodName)) {
                out.print(42);
            }
        }
    }

 */
}
