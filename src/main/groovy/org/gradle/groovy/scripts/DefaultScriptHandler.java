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

import groovy.lang.Script;
import groovy.lang.GroovyShell;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.net.URLClassLoader;

import org.gradle.util.Clock;
import org.gradle.util.GFileUtils;
import org.gradle.util.WrapUtil;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilationFailedException;

/**
 * @author Hans Dockter
 */
public class DefaultScriptHandler implements IScriptHandler {
    private Logger logger = LoggerFactory.getLogger(DefaultScriptHandler.class);

    public Script createScript(String scriptText, ClassLoader classLoader, String scriptName, Class scriptBaseClass) {
        logger.debug("Parsing Script:\n{}", scriptText);
        Clock clock = new Clock();
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        GroovyShell groovyShell = new GroovyShell(classLoader, new Binding(), configuration);
        Script script;
        try {
            script = groovyShell.parse(scriptText, scriptName);
        } catch (CompilationFailedException e) {
            throw new GradleScriptException(e, scriptName);
        }
        logger.debug("Timing: Creating script took: {}", clock.getTime());
        return script;
    }

    public Script writeToCache(String scriptText, ClassLoader classLoader, String scriptName, File scriptCacheDir, Class scriptBaseClass) {
        Clock clock = new Clock();
        GFileUtils.deleteDirectory(scriptCacheDir);
        scriptCacheDir.mkdirs();
        CompilerConfiguration configuration = createBaseCompilerConfiguration(scriptBaseClass);
        configuration.setTargetDirectory(scriptCacheDir);
        CompilationUnit unit = new CompilationUnit(configuration, null, new GroovyClassLoader(classLoader));
        unit.addSource(scriptName, new ByteArrayInputStream(scriptText.getBytes()));
        try {
            unit.compile();
        } catch (CompilationFailedException e) {
            throw new GradleScriptException(e, scriptName);
        }
        logger.info("Timing: Writing script to cache at {} took: {}", scriptCacheDir.getAbsolutePath(), clock.getTime());
        return loadFromCache(0, classLoader, scriptName, scriptCacheDir);
    }

    private CompilerConfiguration createBaseCompilerConfiguration(Class scriptBaseClass) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass(scriptBaseClass.getName());
        return configuration;
    }

    public Script loadFromCache(long lastModified, ClassLoader classLoader, String scriptName, File scriptCacheDir) {
        if (scriptCacheDir.lastModified() < lastModified) {
            return null;
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
        logger.info("Timing: Loading script from cache took: {}", clock.getTime());
        return script;
    }

    private File cacheFile(File cacheDirParent, String buildCacheFileName) {
        return new File(new File(cacheDirParent, buildCacheFileName), buildCacheFileName + ".class");
    }
}
