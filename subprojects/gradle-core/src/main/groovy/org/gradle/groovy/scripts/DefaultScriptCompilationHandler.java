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
package org.gradle.groovy.scripts;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.Script;
import groovyjarjarasm.asm.ClassWriter;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.syntax.SyntaxException;
import org.gradle.api.GradleException;
import org.gradle.api.ScriptCompilationException;
import org.gradle.util.Clock;
import org.gradle.util.GFileUtils;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLClassLoader;
import java.security.CodeSource;

/**
 * @author Hans Dockter
 */
public class DefaultScriptCompilationHandler implements ScriptCompilationHandler {
    private Logger logger = LoggerFactory.getLogger(DefaultScriptCompilationHandler.class);

    public void compileToDir(ScriptSource source, ClassLoader classLoader, File scriptCacheDir,
                             Transformer transformer, Class<? extends Script> scriptBaseClass) {
        Clock clock = new Clock();
        GFileUtils.deleteDirectory(scriptCacheDir);
        scriptCacheDir.mkdirs();
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        configuration.setTargetDirectory(scriptCacheDir);
        compileScript(source, classLoader, configuration, transformer);

        logger.debug("Timing: Writing script to cache at {} took: {}", scriptCacheDir.getAbsolutePath(),
                clock.getTime());
    }

    private Class compileScript(final ScriptSource source, ClassLoader classLoader, CompilerConfiguration configuration,
                              final Transformer transformer) {
        logger.info("Compiling {} using {}.", source.getDisplayName(), transformer != null ? transformer.getClass().getSimpleName() : "no transformer");

        GroovyClassLoader groovyClassLoader = new GroovyClassLoader(classLoader, configuration, false) {
            @Override
            protected CompilationUnit createCompilationUnit(CompilerConfiguration compilerConfiguration,
                                                            CodeSource codeSource) {
                CompilationUnit compilationUnit = new CompilationUnit(compilerConfiguration, codeSource, this) {
                    // This creepy bit of code is here to put the full source path of the script into the debug info for
                    // the class.  This makes it possible for a debugger to find the source file for the class.  By default
                    // Groovy will only put the filename into the class, but that does not help a debugger for Gradle
                    // because it does not know where Gradle scripts might live.
                    @Override
                    protected groovyjarjarasm.asm.ClassVisitor createClassVisitor() {
                        return new ClassWriter(ClassWriter.COMPUTE_MAXS) {
                            // ignore the sourcePath that is given by Groovy (this is only the filename) and instead
                            // insert the full path if our script source has a source file
                            @Override
                            public void visitSource(String sourcePath, String debugInfo) {
                                super.visitSource(source.getFileName(), debugInfo);
                            }
                        };
                    }
                };

                if (transformer != null) {
                    transformer.register(compilationUnit);
                }
                return compilationUnit;
            }
        };
        String scriptText = source.getResource().getText();
        String scriptName = source.getClassName();
        Class scriptClass;
        GroovyCodeSource codeSource = new GroovyCodeSource(scriptText == null ? "" : scriptText, scriptName, "/groovy/script");
        try {
            scriptClass = groovyClassLoader.parseClass(codeSource, false);
        } catch (MultipleCompilationErrorsException e) {
            SyntaxException syntaxError = e.getErrorCollector().getSyntaxError(0);
            Integer lineNumber = syntaxError == null ? null : syntaxError.getLine();
            throw new ScriptCompilationException(String.format("Could not compile %s.", source.getDisplayName()), e, source,
                    lineNumber);
        } catch (CompilationFailedException e) {
            throw new GradleException(String.format("Could not compile %s.", source.getDisplayName()), e);
        }

        if (!hasScriptStatements(scriptName, scriptClass)) {
            // Assume an empty script
            String emptySource = String.format("class %s extends %s { public Object run() { return null } }",
                    source.getClassName(), configuration.getScriptBaseClass().replaceAll("\\$", "."));
            scriptClass = groovyClassLoader.parseClass(emptySource, scriptName);
        }
        return scriptClass;
    }

    private boolean hasScriptStatements(String scriptName, Class scriptClass) {
        return scriptClass != null && scriptClass.getName().equals(scriptName);
    }

    private CompilerConfiguration createBaseCompilerConfiguration(Class<? extends Script> scriptBaseClass) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass(scriptBaseClass.getName());
        return configuration;
    }

    public <T extends Script> Class<? extends T> loadFromDir(ScriptSource source, ClassLoader classLoader, File scriptCacheDir,
                                              Class<T> scriptBaseClass) {
        try {
            URLClassLoader urlClassLoader = new URLClassLoader(WrapUtil.toArray(scriptCacheDir.toURI().toURL()),
                    classLoader);
            return urlClassLoader.loadClass(source.getClassName()).asSubclass(scriptBaseClass);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not load compiled classes for %s from cache.", source.getDisplayName()), e);
        }
    }
}
