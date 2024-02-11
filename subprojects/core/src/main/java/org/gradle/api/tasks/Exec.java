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
package org.gradle.api.tasks;

import org.gradle.work.DisableCachingByDefault;

/**
 * Executes a command line process. Example:
 * <pre class='autoTested'>
 * task stopTomcat(type:Exec) {
 *   workingDir '../tomcat/bin'
 *
 *   //on windows:
 *   commandLine 'cmd', '/c', 'stop.bat'
 *
 *   //on linux
 *   commandLine './stop.sh'
 *
 *   //store the output instead of printing to the console:
 *   standardOutput = new ByteArrayOutputStream()
 *
 *   //extension method stopTomcat.output() can be used to obtain the output:
 *   ext.output = {
 *     return standardOutput.toString()
 *   }
 * }
 * </pre>
 */
@DisableCachingByDefault(because = "Gradle would require more information to cache this task")
public abstract class Exec extends AbstractExecTask<Exec> {
    public Exec() {
        super(Exec.class);
    }
}
