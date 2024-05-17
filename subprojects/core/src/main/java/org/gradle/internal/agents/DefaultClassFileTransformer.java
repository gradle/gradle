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

package org.gradle.internal.agents;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;

class DefaultClassFileTransformer implements ClassFileTransformer {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!(loader instanceof InstrumentingClassLoader)) {
            return null;
        }
        InstrumentingClassLoader instrumentingLoader = (InstrumentingClassLoader) loader;
        try {
            return instrumentingLoader.instrumentClass(className, protectionDomain, classfileBuffer);
        } catch (Throwable th) {
            // Throwing exception from the ClassFileTransformer has no effect - if it happens, the class is loaded unchanged silently.
            // This is not something we want, so we notify the class loader about this.
            instrumentingLoader.transformFailed(className, th);
            return null;
        }
    }

    public static boolean tryInstall() {
        // Installing the same transformer multiple times is very problematic, so additional correctness check is worth it.
        if (!INSTALLED.compareAndSet(false, true)) {
            throw new IllegalStateException("The transformer is already installed in " + DefaultClassFileTransformer.class.getClassLoader());
        }
        return AgentControl.installTransformer(new DefaultClassFileTransformer());
    }
}
