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

package org.gradle.groovy.scripts.internal;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.tools.GroovyClass;
import org.gradle.internal.classpath.transforms.ClassTransforms;
import org.gradle.internal.classpath.transforms.InstrumentingClassTransform;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;

import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter.INSTRUMENTATION_ONLY;

/**
 * Warms up the Groovy script compiler on a background thread when a daemon starts.
 * <p>
 * The first script compilation in a fresh JVM takes a few hundred milliseconds, mostly to load the compiler and
 * the parser grammar and to run that code before the JIT has compiled it. That happens on the critical path of the
 * first build, while evaluating the settings script. Doing it in the background, while the daemon starts and the
 * build is set up, takes it off the critical path.
 * <p>
 * The warm-up compiles a few representative scripts in the same way that build scripts are compiled, instruments
 * the generated classes, and creates a script instance so that the Groovy metaclass machinery is initialized.
 * Failures are ignored.
 */
public final class GroovyScriptCompilerWarmUp {

    /**
     * Set this environment variable to disable the warm-up, for comparing performance while this is a prototype.
     */
    private static final String DISABLE_ENVIRONMENT_VARIABLE = "GRADLE_INTERNAL_DISABLE_GROOVY_WARM_UP";

    private static final String[] SCRIPTS = {
        "pluginManagement {\n" +
            "    includeBuild 'build-logic'\n" +
            "    repositories { gradlePluginPortal(); mavenCentral() }\n" +
            "}\n" +
            "dependencyResolutionManagement {\n" +
            "    repositories { mavenCentral() }\n" +
            "}\n" +
            "rootProject.name = 'warm-up'\n" +
            "include ':a', ':b:c'\n" +
            "include 'd'\n",
        "plugins {\n" +
            "    id 'java-library'\n" +
            "    id 'org.example.plugin' version '1.0' apply false\n" +
            "}\n" +
            "description = \"A ${project.name} module\"\n" +
            "version = '1.0.0-SNAPSHOT'\n" +
            "java {\n" +
            "    withSourcesJar()\n" +
            "}\n" +
            "dependencies {\n" +
            "    api project(':a')\n" +
            "    implementation 'org.slf4j:slf4j-api:2.0.16'\n" +
            "    testImplementation(platform('org.junit:junit-bom:5.11.4'))\n" +
            "}\n" +
            "def generated = layout.buildDirectory.dir('generated')\n" +
            "tasks.register('writeVersion') {\n" +
            "    def v = project.version.toString()\n" +
            "    outputs.dir(generated)\n" +
            "    doLast {\n" +
            "        def f = generated.get().file('version.txt').asFile\n" +
            "        f.parentFile.mkdirs()\n" +
            "        f.text = \"version=${v}\\n\"\n" +
            "    }\n" +
            "}\n" +
            "tasks.named('test', Test) {\n" +
            "    useJUnitPlatform()\n" +
            "    systemProperty 'key', [a: 1, b: [2, 3]]\n" +
            "    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1\n" +
            "}\n" +
            "if (hasProperty('ci')) {\n" +
            "    tasks.withType(JavaCompile).configureEach { options.compilerArgs << '-Werror' }\n" +
            "}\n",
    };

    private GroovyScriptCompilerWarmUp() {
    }

    public static void startInBackground() {
        if (System.getenv(DISABLE_ENVIRONMENT_VARIABLE) != null) {
            return;
        }
        Thread thread = new Thread(GroovyScriptCompilerWarmUp::warmUp, "Groovy script compiler warm-up");
        thread.setDaemon(true);
        thread.start();
    }

    private static void warmUp() {
        try {
            InstrumentingClassTransform transform = new InstrumentingClassTransform(INSTRUMENTATION_ONLY, InstrumentationTypeRegistry.EMPTY);
            for (int i = 0; i < SCRIPTS.length; i++) {
                compile("_WarmUp_" + i, SCRIPTS[i], transform);
            }
        } catch (Throwable ignored) {
            // The warm-up is only an optimization
        }
    }

    private static void compile(String className, String text, InstrumentingClassTransform transform) throws Exception {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setTargetBytecode(CompilerConfiguration.JDK8);
        List<CompilationUnit> units = new ArrayList<>(1);
        try (GroovyClassLoader loader = new GroovyClassLoader(GroovyScriptCompilerWarmUp.class.getClassLoader(), configuration, false) {
            @Override
            protected CompilationUnit createCompilationUnit(CompilerConfiguration config, CodeSource source) {
                CompilationUnit unit = new CustomCompilationUnit(config, source, classNode -> { }, this, Collections.emptyMap());
                units.add(unit);
                return unit;
            }
        }) {
            Class<?> scriptClass = loader.parseClass(new GroovyCodeSource(text, className, "/groovy/script"), false);
            for (GroovyClass groovyClass : units.get(0).getClasses()) {
                ClassTransforms.applyToBytes(transform, groovyClass.getName().replace('.', '/'), groovyClass.getBytes());
            }
            // Creates the script's metaclass, which initializes the Groovy runtime
            scriptClass.getDeclaredConstructor().newInstance();
        }
    }
}
