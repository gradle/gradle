/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.tasks.application;

/**
 * Creates start scripts for launching JVM applications.
 * <p>
 * Example:
 * <pre autoTested=''>
 * task createStartScripts(type: CreateStartScripts) {
 *   outputDir = file('build/sample')
 *   mainClassName = 'org.gradle.test.Main'
 *   applicationName = 'myApp'
 *   classpath = files('path/to/some.jar')
 * }
 * </pre>
 * <p>
 * Note: the Gradle {@code "application"} plugin adds a pre-configured task of this type named {@code "createStartScripts"}.
 * <p>
 * The task generates separate scripts targeted at Microsoft Windows environments and UNIX-like environments (e.g. Linux, Mac OS X).
 * The actual generation is implemented by the {@link #getWindowsStartScriptGenerator()} and {@link #getUnixStartScriptGenerator()} properties, of type
 * {@link org.gradle.jvm.application.scripts.ScriptGenerator}.
 * <p>
 * Example:
 * <pre autoTested=''>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator = new CustomUnixStartScriptGenerator()
 *   windowsStartScriptGenerator = new CustomWindowsStartScriptGenerator()
 * }
 *
 * class CustomUnixStartScriptGenerator implements ScriptGenerator {
 *   void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 *     // implementation
 *   }
 * }
 *
 * class CustomWindowsStartScriptGenerator implements ScriptGenerator {
 *   void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 *     // implementation
 *   }
 * }
 * </pre>
 * <p>
 * The default generators are of the type {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}, with default templates.
 * This templates can be changed via the {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator#setTemplate(org.gradle.api.resources.TextResource)} method.
 * <p>
 * The default implementations used by this task use <a href="http://docs.groovy-lang.org/latest/html/documentation/template-engines.html#_simpletemplateengine">Groovy's SimpleTemplateEngine</a>
 * to parse the template, with the following variables available:
 * <p>
 * <ul>
 * <li>{@code applicationName}</li>
 * <li>{@code optsEnvironmentVar}</li>
 * <li>{@code exitEnvironmentVar}</li>
 * <li>{@code mainClassName}</li>
 * <li>{@code defaultJvmOpts}</li>
 * <li>{@code appNameSystemProperty}</li>
 * <li>{@code appHomeRelativePath}</li>
 * <li>{@code classpath}</li>
 * </ul>
 * <p>
 * Example:
 * <p>
 * <pre>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator.template = resources.text.fromFile('customUnixStartScript.txt')
 *   windowsStartScriptGenerator.template = resources.text.fromFile('customWindowsStartScript.txt')
 * }
 * </pre>
 */
public class CreateStartScripts extends org.gradle.jvm.application.tasks.CreateStartScripts {
}
