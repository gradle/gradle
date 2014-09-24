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

package org.gradle.jvm.internal.configure;

import org.gradle.api.Action;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;

import java.io.File;

public class JarBinarySpecInitializer implements Action<JarBinarySpec> {

    private final File binariesDir;
    private final File classesDir;

    public JarBinarySpecInitializer(File buildDir) {
        binariesDir = new File(buildDir, "jars");
        classesDir = new File(buildDir, "classes");
    }

    public void execute(JarBinarySpec jarBinarySpec) {
        JarBinarySpecInternal jarBinarySpecInternal = (JarBinarySpecInternal) jarBinarySpec;

        String outputBaseName = jarBinarySpecInternal.getNamingScheme().getOutputDirectoryBase();
        File outputDir = new File(classesDir, outputBaseName);
        jarBinarySpec.setClassesDir(outputDir);
        jarBinarySpec.setResourcesDir(outputDir);
        jarBinarySpec.setJarFile(new File(binariesDir, String.format("%s/%s.jar", outputBaseName, jarBinarySpecInternal.getLibrary().getName())));
    }
}
