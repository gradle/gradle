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
 * @author Hans Dockter
 */
public interface IScriptProcessor {
    Script createScriptFromFile(File cacheDir, File buildFile, String scriptAttachement,
                        CacheUsage cacheUsage, ClassLoader classLoader, Class scriptBaseClass);

    Script createScriptFromText(String inMemoryScriptText, String scriptAttachement,
                        String scriptName, ClassLoader classLoader, Class scriptBaseClass);
}
