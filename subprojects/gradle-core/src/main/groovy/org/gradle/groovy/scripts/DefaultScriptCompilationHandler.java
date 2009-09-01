/*
 * Copyright 2007-2008 the original author or authors.
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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.syntax.SyntaxException;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.util.Clock;
import org.gradle.util.GFileUtils;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.net.URLClassLoader;
import java.security.CodeSource;
import static java.util.Collections.*;

/**
 * @author Hans Dockter
 */
public class DefaultScriptCompilationHandler implements ScriptCompilationHandler {
    private Logger logger = LoggerFactory.getLogger(DefaultScriptCompilationHandler.class);
    static final String DEBUGINFO_KEY = "sourcefile";

    private final CachePropertiesHandler cachePropertiesHandler;

    public DefaultScriptCompilationHandler(CachePropertiesHandler cachePropertiesHandler) {
        this.cachePropertiesHandler = cachePropertiesHandler;
    }

    public <T extends Script> T createScriptOnTheFly(ScriptSource source, ClassLoader classLoader,
                                                     Transformer transformer,
                                                     Class<T> scriptBaseClass) {
        Clock clock = new Clock();
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        Class scriptClass = compileScript(source, classLoader, configuration, transformer);
        T script = scriptBaseClass.cast(InvokerHelper.createScript(scriptClass, new Binding()));

        logger.debug("Timing: Creating script took: {}", clock.getTime());
        return script;
    }

    public void writeToCache(ScriptSource source, ClassLoader classLoader, File scriptCacheDir,
                             Transformer transformer, Class<? extends Script> scriptBaseClass) {
        Clock clock = new Clock();
        GFileUtils.deleteDirectory(scriptCacheDir);
        scriptCacheDir.mkdirs();
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        configuration.setTargetDirectory(scriptCacheDir);
        compileScript(source, classLoader, configuration, transformer);

        cachePropertiesHandler.writeProperties(source, scriptCacheDir, singletonMap(DEBUGINFO_KEY, source.getFileName()));
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
                    protected ClassVisitor createClassVisitor() {
                        return new ClassWriter(true) {
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
        String scriptText = source.getText();
        String scriptName = source.getClassName();
        Class scriptClass;
        try {
            scriptClass = groovyClassLoader.parseClass(scriptText == null ? "" : scriptText, scriptName);
        } catch (MultipleCompilationErrorsException e) {
            SyntaxException syntaxError = e.getErrorCollector().getSyntaxError(0);
            Integer lineNumber = syntaxError == null ? null : syntaxError.getLine();

            throw new GradleScriptException(String.format("Could not compile %s.", source.getDisplayName()), e, source,
                    lineNumber);
        } catch (CompilationFailedException e) {
            throw new GradleException(String.format("Could not compile %s.", source.getDisplayName()), e);
        }

        if (scriptClass == null) {
            // Assume an empty script
            String emptySource = String.format("class %s extends %s { public Object run() { return null } }",
                    source.getClassName(), configuration.getScriptBaseClass().replaceAll("\\$", "."));
            scriptClass = groovyClassLoader.parseClass(emptySource, scriptName);
        }
        return scriptClass;
    }

    private CompilerConfiguration createBaseCompilerConfiguration(Class<? extends Script> scriptBaseClass) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass(scriptBaseClass.getName());
        return configuration;
    }

    public <T extends Script> T loadFromCache(ScriptSource source, ClassLoader classLoader, File scriptCacheDir,
                                              Class<T> scriptBaseClass) {
        CachePropertiesHandler.CacheState cacheState = cachePropertiesHandler.getCacheState(source, scriptCacheDir, singletonMap(DEBUGINFO_KEY, source.getFileName()));
        if (cacheState == CachePropertiesHandler.CacheState.INVALID) {
            return null;
        }
        Clock clock = new Clock();
        Script script;
        try {
            URLClassLoader urlClassLoader = new URLClassLoader(WrapUtil.toArray(scriptCacheDir.toURI().toURL()),
                    classLoader);
            script = (Script) urlClassLoader.loadClass(source.getClassName()).newInstance();
        } catch (ClassNotFoundException e) {
            logger.debug("Class not in cache: ", e);
            return null;
        } catch (Exception e) {
            throw new GradleException(e);
        }
        if (!scriptBaseClass.isInstance(script)) {
            return null;
        }
        logger.debug("Timing: Loading script from cache took: {}", clock.getTime());
        return scriptBaseClass.cast(script);
    }

    public CachePropertiesHandler getCachePropertyHandler() {
        return cachePropertiesHandler;
    }
}
