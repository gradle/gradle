/*
 * Copyright 2009 the original author or authors.
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
package org.gradle;

import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.DefaultScriptSourceMappingHandler;

import java.io.File;

/**
 * This class exists for use by external tools (IDEs) to support debugging
 * of Gradle scripts.  Do not move or rename it as this will break such tools.
 * Because such tools are expected to use reflection to access this class,
 * changing the names or signatures of methods may also break such tools.
 *
 * By using this class, external tools can avoid making assumptions about the
 * operation of Gradle, which means that Gradle can be modified without breaking
 * the debugging support of such tools.
 *
 * @author John Murph
 */
public class DebugHelper
{
    private final DefaultScriptSourceMappingHandler mappingHandler;

    /**
     * Creates a new DebugHelper that retrieves information from a project rooted
     * at <code>rootProjectDir</code>.  Debug information is stored below the
     * project root directory, so this class will only provide debug information
     * for scripts run from the given root directory.
     *
     * @param  rootProjectDir The directory under which to find the debug information.
     */
    public DebugHelper(File rootProjectDir) {
        mappingHandler = new DefaultScriptSourceMappingHandler(rootProjectDir);
    }

    /**
     * Gradle compiles scripts into classes for execution within the Java VM.  A
     * debugger needs to know what class name will be generated for a given script
     * so that break points can be registered with the VM.
     *
     * @param scriptFile the script file for which the class name is desired.  Cannot
     *        be null.
     * @return the fully qualified class name of the generated script.  Currently
     *         Gradle always generates scripts into the default package, but that
     *         should not be assumed.  If the given scriptFile cannot be turned into
     *         a class (because it does not exist, for example) then null is returned.
     */
    public String getClassNameForScript(File scriptFile) {
        assert scriptFile != null;

        FileScriptSource source = new FileScriptSource("debug helper", scriptFile);
        return source.getClassName();
    }

    /**
     * For the given classname determine the Gradle source script.  This will only
     * return non-null for scripts that have been compiled by Gradle for this
     * project (or all associated projects in a multi-project build).  This allows
     * a debugger to show the source file for a class.
     *
     * @param className The class name.  Cannot be null.
     * @return the script file for this class.  It is possible that the returned
     *         file object does not represent a actual file (i.e. exists() might
     *         return false).  If the script file cannot be determined, null is
     *         returned.
     */
    public File getScriptForClassName(String className) {
        assert className != null;

        return mappingHandler.getSource(className);
    }
}

