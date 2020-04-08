/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.internal.classpath.ClassPath;

public class DefaultClassPathProvider implements ClassPathProvider {
    private final ModuleRegistry moduleRegistry;

    public DefaultClassPathProvider(ModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
    }

    @Override
    public ClassPath findClassPath(String name) {
        if (name.equals("GRADLE_RUNTIME")) {
            return moduleRegistry.getModule("gradle-launcher").getAllRequiredModulesClasspath();
        }
        if (name.equals("GRADLE_INSTALLATION_BEACON")) {
            return moduleRegistry.getModule("gradle-installation-beacon").getImplementationClasspath();
        }
        if (name.equals("GROOVY-COMPILER")) {
            ClassPath classpath = ClassPath.EMPTY;
            classpath = classpath.plus(moduleRegistry.getModule("gradle-language-groovy").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("groovy-all").getClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("asm").getClasspath());
            classpath = addJavaCompilerModules(classpath);
            return classpath;
        }
        if (name.equals("SCALA-COMPILER")) {
            ClassPath classpath = ClassPath.EMPTY;
            classpath = classpath.plus(moduleRegistry.getModule("gradle-language-scala").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-scala").getImplementationClasspath());
            classpath = addJavaCompilerModules(classpath);
            return classpath;
        }
        if (name.equals("PLAY-COMPILER")) {
            ClassPath classpath = ClassPath.EMPTY;
            classpath = classpath.plus(moduleRegistry.getModule("gradle-platform-play").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-javascript").getImplementationClasspath());
            classpath = addJavaCompilerModules(classpath);
            return classpath;
        }
        if (name.equals("JAVA-COMPILER")) {
            return addJavaCompilerModules(ClassPath.EMPTY);
        }
        if (name.equals("JAVA-COMPILER-PLUGIN")) {
            return addJavaCompilerModules(moduleRegistry.getModule("gradle-java-compiler-plugin").getImplementationClasspath());
        }
        if (name.equals("ANT")) {
            ClassPath classpath = ClassPath.EMPTY;
            classpath = classpath.plus(moduleRegistry.getExternalModule("ant").getClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("ant-launcher").getClasspath());
            return classpath;
        }

        return null;
    }

    private ClassPath addJavaCompilerModules(ClassPath classpath) {
        classpath = classpath.plus(moduleRegistry.getModule("gradle-language-java").getImplementationClasspath());
        classpath = classpath.plus(moduleRegistry.getModule("gradle-language-jvm").getImplementationClasspath());
        classpath = classpath.plus(moduleRegistry.getModule("gradle-platform-base").getImplementationClasspath());
        return classpath;
    }
}
