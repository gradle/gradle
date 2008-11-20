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

import java.io.File;

import org.gradle.CacheUsage;

/**
 * Loads scripts from text source into a {@link Script} object.
 *
 * @author Hans Dockter
 */
public interface IScriptProcessor {

    /**
     * Loads a script from the given source, creating a class with the given base class and ClassLoader.
     */
    <T extends ScriptWithSource> T createScript(ScriptSource source, ClassLoader classLoader, Class<T> scriptBaseClass);
}
