/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 * todo: integration test
 */
class AntJavac {
    private static Logger logger = LoggerFactory.getLogger(AntJavac)

    static final String CLASSPATH_ID = 'compile.classpath'

    void execute(List sourceDirs, List includes, List excludes, File targetDir, List classpath, String sourceCompatibility,
                 String targetCompatibility, CompileOptions compileOptions, AntBuilder ant) {
        ant.mkdir(dir: targetDir.absolutePath)
        createAntClassPath(ant, classpath)
        Map otherArgs = [
                includeAntRuntime: false,
                srcdir: sourceDirs.join(':'),
                destdir: targetDir,
                classpathref: CLASSPATH_ID,
                target: targetCompatibility,
                source: sourceCompatibility
        ]
        ant.javac(otherArgs + compileOptions.optionMap()) {
            includes.each {
                include(name: it)
            }
            excludes.each {
                exclude(name: it)
            }
        }
    }

    private void createAntClassPath(AntBuilder ant, List classpath) {
        ant.path(id: CLASSPATH_ID) {
            classpath.each {
                logger.debug("Add $it to Ant classpath!")
                pathelement(location: it)
            }
        }
    }
}
