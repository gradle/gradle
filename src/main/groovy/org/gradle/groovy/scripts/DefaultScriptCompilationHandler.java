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
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.util.Clock;
import org.gradle.util.GFileUtils;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URLClassLoader;

/**
 * @author Hans Dockter
 */
public class DefaultScriptCompilationHandler implements ScriptCompilationHandler {
    private Logger logger = LoggerFactory.getLogger(DefaultScriptCompilationHandler.class);

    private CachePropertiesHandler cachePropertiesHandler;

    public DefaultScriptCompilationHandler(CachePropertiesHandler cachePropertiesHandler) {
        this.cachePropertiesHandler = cachePropertiesHandler;
    }

    public Script createScriptOnTheFly(ScriptSource source, ClassLoader classLoader, Class<? extends Script> scriptBaseClass) {
        Clock clock = new Clock();
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        GroovyShell groovyShell = new GroovyShell(classLoader, new Binding(), configuration);
        String scriptText = source.getText();
        String scriptName = source.getClassName();
        Script script;
        try {
            script = groovyShell.parse(scriptText == null ? "" : scriptText, scriptName);
        } catch (MultipleCompilationErrorsException e) {
            throw new GradleScriptException(String.format("Could not compile %s.", source.getDisplayName()), e,
                    source, e.getErrorCollector().getSyntaxError(0).getLine());
        } catch (CompilationFailedException e) {
            throw new GradleException(String.format("Could not compile %s.", source.getDisplayName()), e);
        }
        if (!scriptBaseClass.isInstance(script)) {
            // Assume an empty script
            return new EmptyScript();
        }

        logger.debug("Timing: Creating script took: {}", clock.getTime());
        return script;
    }

    public void writeToCache(ScriptSource source, ClassLoader classLoader, File scriptCacheDir, Class<? extends Script> scriptBaseClass) {
        Clock clock = new Clock();
        GFileUtils.deleteDirectory(scriptCacheDir);
        scriptCacheDir.mkdirs();
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        configuration.setTargetDirectory(scriptCacheDir);
        CompilationUnit unit = new CompilationUnit(configuration, null, new GroovyClassLoader(classLoader));
        String scriptName = source.getClassName();
        String scriptText = source.getText();
        unit.addSource(scriptName, new ByteArrayInputStream(scriptText == null ? new byte[0] : scriptText.getBytes()));
        try {
            unit.compile();
        } catch (MultipleCompilationErrorsException e) {
            throw new GradleScriptException(String.format("Could not compile %s.", source.getDisplayName()), e,
                    source, e.getErrorCollector().getSyntaxError(0).getLine());
        } catch (CompilationFailedException e) {
            throw new GradleException(String.format("Could not compile %s.", source.getDisplayName()), e);
        }
        boolean emptyScript = false;
        if (unit.getClasses().isEmpty()) {
            emptyScript = true;
        }
        cachePropertiesHandler.writeProperties(scriptText, scriptCacheDir, emptyScript);
        logger.debug("Timing: Writing script to cache at {} took: {}", scriptCacheDir.getAbsolutePath(), clock.getTime());
    }

    private CompilerConfiguration createBaseCompilerConfiguration(Class<? extends Script> scriptBaseClass) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass(scriptBaseClass.getName());
        return configuration;
    }

    public Script loadFromCache(ScriptSource source, ClassLoader classLoader, File scriptCacheDir, Class<? extends Script> scriptBaseClass) {
        String scriptText = source.getText();
        String scriptName = source.getClassName();
        CachePropertiesHandler.CacheState cacheState = cachePropertiesHandler.getCacheState(scriptText, scriptCacheDir);
        if (cacheState == CachePropertiesHandler.CacheState.INVALID) {
            return null;
        } else if (cacheState == CachePropertiesHandler.CacheState.EMPTY_SCRIPT) {
            return new EmptyScript();
        }
        Clock clock = new Clock();
        Script script;
        try {
            URLClassLoader urlClassLoader = new URLClassLoader(WrapUtil.toArray(scriptCacheDir.toURI().toURL()),
                    classLoader);
            script = (Script) urlClassLoader.loadClass(scriptName).newInstance();
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
        return script;
    }

    public CachePropertiesHandler getCachePropertyHandler() {
        return cachePropertiesHandler;
    }
}
