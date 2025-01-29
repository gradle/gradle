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
        if (name.equals("GRADLE_INSTALLATION_BEACON")) {
            return moduleRegistry.getModule("gradle-installation-beacon").getImplementationClasspath();
        }
        if (name.equals("GROOVY-COMPILER")) {
            return moduleRegistry.getModule("gradle-groovy-compiler-worker").getAllRequiredModulesClasspath();
        }
        if (name.equals("SCALA-COMPILER")) {
            return moduleRegistry.getModule("gradle-scala-compiler-worker").getAllRequiredModulesClasspath();
        }
        if (name.equals("JAVA-COMPILER")) {
            return moduleRegistry.getModule("gradle-java-compiler-worker").getAllRequiredModulesClasspath();
        }
        if (name.equals("DEPENDENCIES-EXTENSION-COMPILER")) {
            // Classpath required for generating version catalog extensions
            ClassPath classpath = ClassPath.EMPTY;
            classpath = classpath.plus(moduleRegistry.getModule("gradle-base-services").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-classloaders").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-core-api").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-core").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-stdlib-java-extensions").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-logging").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getModule("gradle-dependency-management").getImplementationClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("javax.inject").getClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("jspecify").getClasspath());
            return classpath;
        }
        if (name.equals("JAVA-COMPILER-PLUGIN")) {
            // Classpath required for the incremental Java annotation processor.
            // Should be the annotation processor implementation classpath + the Java compiler classpath.
            // TODO: Determine if this is still necessary. See git history for org.gradle.api.internal.tasks.compile.JdkTools
            ClassPath classpath = ClassPath.EMPTY;
            classpath = classpath.plus(moduleRegistry.getModule("gradle-java-compiler-plugin").getImplementationClasspath());
            classpath = classpath.plus(findClassPath("JAVA-COMPILER"));
            return classpath;
        }
        if (name.equals("ANT")) {
            ClassPath classpath = ClassPath.EMPTY;
            classpath = classpath.plus(moduleRegistry.getExternalModule("ant").getClasspath());
            classpath = classpath.plus(moduleRegistry.getExternalModule("ant-launcher").getClasspath());
            return classpath;
        }

        return null;
    }

}
