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

package org.gradle.build.samples

/**
 * @author Hans Dockter
 */
class WaterProjectCreator {
    final static String NL = System.properties['line.separator']

    final static String HELLO_CLAUSE = "Hello, I'm "
    final static String CHILDREN_TEXT = 'I love water.'
    final static String WATER_INFO = 'As you all know, I cover three quarters of this planet!'
    final static String BLUE_WHALE_INFO = "I'm the largets animal which has ever lived on this planet!"
    final static String KRILL_INFO = "The weight of my species in summer is twice as heavy as all human beings!"
    final static String PHYTOPLANKTON_INFO = "I produce as much oxygen as all the other plants on earth together!"

    final static String WATER_NAME = 'water'
    final static String BLUE_WHALE_NAME = 'bluewhale'
    final static String KRILL_NAME = 'krill'
    final static String PHYTOPLANKTON_NAME = 'phytoplankton'


    static void createProjectTree(File baseDir) {
        String gradleSettingsScript = "include 'bluewhale', 'krill', 'phytoplankton'"

        // ---------------------------- water
        String waterScript = """
    import org.gradle.api.Task

    childrenDependOnMe()

    allprojects*.createTask('hello') { Task task ->
        println \"$HELLO_CLAUSE\$task.project.name\"
    }

    subprojects*.hello*.doLast {
        println '$CHILDREN_TEXT'
    }

    hello.doLast {
        println '$WATER_INFO'
    }
    """
        // ---------------------------- blue whale
        String bluewhaleScript = """
    dependsOn(':$KRILL_NAME')

    hello.doLast {
        println "$BLUE_WHALE_INFO"
    }
    """
        // ---------------------------- krill
        String krillScript = """
    dependsOn(':$PHYTOPLANKTON_NAME')

    hello.doLast {
        println "$KRILL_INFO"
    }
    """
        // ---------------------------- phytoplankton
        String phytoplanktonScript = """
    hello.doLast {
        println "$PHYTOPLANKTON_INFO"
    }
    """
        String buildScriptName = 'gradle.groovy'
        File water = new File(baseDir, WATER_NAME)
        water.mkdir()
        new File(water, buildScriptName).withPrintWriter {PrintWriter writer -> writer.write(waterScript)}
        new File(water, 'gradlesettings.groovy').withPrintWriter {PrintWriter writer -> writer.write(gradleSettingsScript)}
        File bluewhale = new File(water, BLUE_WHALE_NAME)
        bluewhale.mkdir()
        new File(bluewhale, buildScriptName).withPrintWriter {PrintWriter writer -> writer.write(bluewhaleScript)}
        File krill = new File(water, KRILL_NAME)
        krill.mkdir()
        new File(krill, buildScriptName).withPrintWriter {PrintWriter writer -> writer.write(krillScript)}
        File phytoplankton = new File(water, PHYTOPLANKTON_NAME)
        phytoplankton.mkdir()
        new File(phytoplankton, buildScriptName).withPrintWriter {PrintWriter writer -> writer.write(phytoplanktonScript)}

    }


}
