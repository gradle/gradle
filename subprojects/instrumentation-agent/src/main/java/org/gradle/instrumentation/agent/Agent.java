/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.instrumentation.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

/**
 * An entry point for the on-the-fly bytecode instrumentation agent.
 */
public class Agent {
    private static volatile Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation inst) {
        doMain(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        doMain(inst);
    }

    static void doMain(Instrumentation inst) {
        instrumentation = inst;
    }

    @SuppressWarnings("unused")  // Used reflectively.
    public static boolean isApplied() {
        return instrumentation != null;
    }

    @SuppressWarnings("unused")  // Used reflectively.
    public static boolean installTransformer(ClassFileTransformer transformer) {
        Instrumentation inst = instrumentation;
        if (inst != null) {
            inst.addTransformer(transformer);
            return true;
        }
        return false;
    }
}
