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
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.util.Clock;
import org.gradle.util.GFileUtils;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public Script createScriptOnTheFly(ScriptSource source, ClassLoader classLoader,
                                       Class<? extends Script> scriptBaseClass) {
        Clock clock = new Clock();
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        Class scriptClass = parseScript(source, classLoader, configuration);
        if (scriptClass == null) {
            // Assume an empty script
            return new EmptyScript();
        }
        Script script = InvokerHelper.createScript(scriptClass, new Binding());

        logger.debug("Timing: Creating script took: {}", clock.getTime());
        return script;
    }

    public void writeToCache(ScriptSource source, ClassLoader classLoader, File scriptCacheDir,
                             Class<? extends Script> scriptBaseClass) {
        Clock clock = new Clock();
        GFileUtils.deleteDirectory(scriptCacheDir);
        scriptCacheDir.mkdirs();
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        configuration.setTargetDirectory(scriptCacheDir);
        parseScript(source, classLoader, configuration);

        cachePropertiesHandler.writeProperties(source.getText(), scriptCacheDir);
        logger.debug("Timing: Writing script to cache at {} took: {}", scriptCacheDir.getAbsolutePath(),
                clock.getTime());
    }

    private Class parseScript(ScriptSource source, ClassLoader classLoader, CompilerConfiguration configuration) {
        GroovyClassLoader groovyClassLoader = new GroovyClassLoader(classLoader, configuration, false);
        String scriptText = source.getText();
        String scriptName = source.getClassName();
        Class scriptClass;
        try {
            scriptClass = groovyClassLoader.parseClass(scriptText == null ? "" : scriptText, scriptName);
        } catch (MultipleCompilationErrorsException e) {
            throw new GradleScriptException(String.format("Could not compile %s.", source.getDisplayName()), e, source,
                    e.getErrorCollector().getSyntaxError(0).getLine());
        } catch (CompilationFailedException e) {
            throw new GradleException(String.format("Could not compile %s.", source.getDisplayName()), e);
        }
        return scriptClass;
    }

    private CompilerConfiguration createBaseCompilerConfiguration(Class<? extends Script> scriptBaseClass) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass(scriptBaseClass.getName());
        return configuration;
    }

    public Script loadFromCache(ScriptSource source, ClassLoader classLoader, File scriptCacheDir,
                                Class<? extends Script> scriptBaseClass) {
        String scriptText = source.getText();
        String scriptName = source.getClassName();
        CachePropertiesHandler.CacheState cacheState = cachePropertiesHandler.getCacheState(scriptText, scriptCacheDir);
        if (cacheState == CachePropertiesHandler.CacheState.INVALID) {
            return null;
        }
        if (!hasCompiledClasses(scriptCacheDir)) {
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

    private boolean hasCompiledClasses(File scriptCacheDir) {
        for (String fileName : scriptCacheDir.list()) {
            if (fileName.endsWith(".class")) {
                return true;
            }
        }
        return false;
    }

    public CachePropertiesHandler getCachePropertyHandler() {
        return cachePropertiesHandler;
    }
}
