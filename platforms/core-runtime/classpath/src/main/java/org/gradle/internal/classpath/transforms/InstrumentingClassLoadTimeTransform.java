/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.gradle.internal.classloader.ProtectionDomains;
import org.gradle.internal.classpath.ClassLoadTimeTransform;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;

/**
 * Runs {@link InstrumentingClassTransform} at class-load time against bytes supplied
 * by the JVM, so that Gradle's instrumentation composes with any third-party
 * {@link java.lang.instrument.ClassFileTransformer} that ran earlier.
 * <p>
 * The filter applied depends on the origin of the class, matching the ahead-of-time pipeline:
 * project dependencies are instrumented only, while everything else is instrumented and upgraded.
 * Property-upgrade reporting on project dependencies is not reproduced here. Classes whose code
 * source cannot be resolved to a file are treated as external.
 */
public final class InstrumentingClassLoadTimeTransform implements ClassLoadTimeTransform {

    private final ClassTransform externalTransform;
    private final ClassTransform projectTransform;
    private final Set<File> projectOriginFiles;

    public InstrumentingClassLoadTimeTransform(
        BytecodeInterceptorFilter externalFilter,
        InstrumentationTypeRegistry externalTypeRegistry,
        BytecodeInterceptorFilter projectFilter,
        InstrumentationTypeRegistry projectTypeRegistry,
        Set<File> projectOriginFiles
    ) {
        this.externalTransform = new InstrumentingClassTransform(externalFilter, externalTypeRegistry);
        this.projectTransform = new InstrumentingClassTransform(projectFilter, projectTypeRegistry);
        this.projectOriginFiles = normalize(projectOriginFiles);
    }

    @Override
    public byte[] transform(@Nullable ProtectionDomain protectionDomain, String className, byte[] classfileBuffer) {
        ClassTransform transform = isProjectOrigin(protectionDomain) ? projectTransform : externalTransform;
        return ClassTransforms.applyToBytes(transform, className, classfileBuffer);
    }

    boolean isProjectOrigin(@Nullable ProtectionDomain protectionDomain) {
        File codeSourceFile = ProtectionDomains.codeSourceFileOf(protectionDomain);
        return codeSourceFile != null && projectOriginFiles.contains(normalize(codeSourceFile));
    }

    private static Set<File> normalize(Set<File> files) {
        Set<File> result = new HashSet<>(files.size());
        for (File file : files) {
            result.add(normalize(file));
        }
        return result;
    }

    private static File normalize(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }
}
