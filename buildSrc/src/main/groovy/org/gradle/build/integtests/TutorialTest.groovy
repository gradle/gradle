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

package org.gradle.build.integtests

import org.gradle.build.samples.TutorialCreator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class TutorialTest {
    private static Logger logger = LoggerFactory.getLogger(TutorialTest)

    static void execute(File tutorialOutputDir) {
        Map scripts = TutorialCreator.scripts()
        scripts.each {entry ->
            logger.info("Test tutorial script: ${entry.key}file")
            entry.value[1](outputAfterFirstLine(new File(tutorialOutputDir, "${entry.key}file.out")))
        }
    }

    static String outputAfterFirstLine(File output) {
        String nl = System.properties['line.separator']
        String text = output.text
        int index = text.indexOf(nl)
        if (index >= 0) {
            text = text.substring(text.indexOf(nl) + nl.size())
        }

        text
    }
}