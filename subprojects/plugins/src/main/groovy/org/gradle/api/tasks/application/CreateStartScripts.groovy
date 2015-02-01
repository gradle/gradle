/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.tasks.application

import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.gradle.util.GUtil

/**
 * <p>A {@link org.gradle.api.Task} for creating OS dependent start scripts.</p>
 */
public class CreateStartScripts extends Copy {

    /**
     * The directory to write the scripts into.
     */
    File outputDir

    /**
     * The application's main class.
     */
    @Input
    String mainClassName

    /**
     * The application's default JVM options.
     */
    @Input
    @Optional
    Iterable<String> defaultJvmOpts = []

    /**
     * The application's name.
     */
    @Input
    String applicationName

    String optsEnvironmentVar

    String exitEnvironmentVar

    /**
     * The class path for the application.
     */
    @InputFiles
    FileCollection classpath

    /**
     * A spec for making unix scripts
     * By default contains 'unixStartScript.txt' template, which you can exclude if you wish
     * By default a StartScriptGenerator-based filter is applied for all of the templates
     * You can add your custom templates via CopySpec.from
     * You can add your own filters (they won't get same parameters as the default one though) and stuff like in usual CopySpec
     *
     * <p> Examples
     * <pre autoTested='true'>
     *  startScripts {
     *      unixStartScripts.from('src/main/resources/scripts') {
     *          exclude('myscript.bat')
     *      }
     *      unixStartScripts.exclude('**&#47unixStartScript.txt')
     *  }
     * </pre>
     */
    CopySpec unixStartScripts

    /**
     * A spec for making windows scripts
     * By default contains 'windowsStartScript.txt' template, which you can exclude if you wish
     * By default a StartScriptGenerator-based filter is applied for all of the templates
     * You can add your custom templates via CopySpec.from
     * You can add your own filters (they won't get same parameters as the default one though) and stuff like in usual CopySpec
     *
     * <p> Examples
     * <pre autoTested='true'>
     *  startScripts {
     *      windowsStartScripts.from('src/main/resources/scripts') {
     *          exclude('myscript')
     *      }
     *      windowsStartScripts.exclude('**&#windowsStartScript.txt')
     *  }
     * </pre>
     */
    CopySpec windowsStartScripts

    Closure quoteJvmOptsClosure

    Map<String, String> tokens = [:]

    /**
     *
     * A convenient method for adding extra tokens for the ReplaceToken filter (in case if you need some extras in your custom template)
     *
     * @param name
     * @param value
     */
    public void token(String name, String value) {
        tokens.put(name, value)
    }

    /**
     *
     * This method allows you to specify your own quoting for the JVM opts.
     * You might need this if you want to pass an environment variable as a value for some JVM option
     * Would be called on each JVM opt specified
     * <p> Examples:
     * <pre autoTested='true'>
     * startScripts {
     *  defaultJvmOpts = ['-Dmyopt=']
     *  quoteJvmOpts { system, jvmOpt ->
     *     switch(jvmOpt) {
     *      case '-Dmyopt=': return system.equals('nix') ? (jvmOpt + '\$MY_ENV_VAR') : (jvmOpt + '%MY_ENV_VAR%')
     *      default: return jvmOpt
     *     }
     *  }
     * }
     * < /pre>
     * @param closure system (one of 'nix' or 'win') and a current jvm opt would be passed into
     */
    public void quoteJvmOpts(Closure closure) {
        this.quoteJvmOptsClosure = closure //keeping original delegate
    }

    /**
     * Returns the name of the application's OPTS environment variable.
     */
    @Input
    String getOptsEnvironmentVar() {
        if (optsEnvironmentVar) {
            return optsEnvironmentVar
        }
        if (!getApplicationName()) {
            return null
        }
        return "${GUtil.toConstant(getApplicationName())}_OPTS"
    }

    @Input
    String getExitEnvironmentVar() {
        if (exitEnvironmentVar) {
            return exitEnvironmentVar
        }
        if (!getApplicationName()) {
            return null
        }
        return "${GUtil.toConstant(getApplicationName())}_EXIT_CONSOLE"
    }

}
