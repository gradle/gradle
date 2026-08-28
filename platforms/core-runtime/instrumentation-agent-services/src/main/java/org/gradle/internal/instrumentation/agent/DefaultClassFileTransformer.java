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

package org.gradle.internal.instrumentation.agent;

import org.gradle.internal.classloader.InstrumentingClassLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;

class DefaultClassFileTransformer implements ClassFileTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClassFileTransformer.class);
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    @Override
    public byte @Nullable [] transform(
        @Nullable ClassLoader loader,
        @Nullable String className,
        @Nullable Class<?> classBeingRedefined,
        @Nullable ProtectionDomain protectionDomain,
        byte[] classfileBuffer
    ) {
        if (!(loader instanceof InstrumentingClassLoader)) {
            return null;
        }
        InstrumentingClassLoader instrumentingLoader = (InstrumentingClassLoader) loader;
        byte[] instrumented = doTransform(instrumentingLoader, className, protectionDomain, classfileBuffer);
        if (classBeingRedefined != null && instrumented != null && className != null && !instrumentingLoader.canReinstrumentClasses()) {
            // Another agent requested a class on the build script classpath to be redefined (i.e. it supplied its new bytecode).
            // On the substitution path we serve bytecode captured during the artifact transform and cannot apply the freshly compiled definition,
            // so the swap silently has no effect.
            // Warn so the developer isn't misled into thinking the edit was applied. A typical use case is the debugger requested Hot Code Replace.
            // In most other cases, we detect the agent and set up the runtime-transformation pipeline.
            LOGGER.warn("Redefinition (e.g. due to Hot Code Replace) of the class {} had no effect. " +
                "Gradle serves pre-instrumented bytecode for buildscript and plugin classes, so the recompiled definition is ignored. " +
                "Restart the build to pick up the change.",
                className.replace('/', '.')
            );
        }
        return instrumented;
    }

    private byte @Nullable [] doTransform(InstrumentingClassLoader loader, @Nullable String className, @Nullable ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            return loader.instrumentClass(className, protectionDomain, classfileBuffer);
        } catch (Throwable th) {
            // Throwing exception from the ClassFileTransformer has no effect - if it happens, the class is loaded unchanged silently.
            // This is not something we want, so we notify the class loader about this.
            loader.transformFailed(className, th);
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
