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


/**
 * @author Hans Dockter
 */
class WaterProject {
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

    static void execute(String gradleHome, String samplesDirName) {
        File waterDir = new File(samplesDirName, WATER_NAME)
        String taskName = 'hello'
        Map result = Executer.execute(gradleHome, waterDir.absolutePath, [taskName])
        assert result.output == list2text([intro(WATER_NAME), WATER_INFO,
                intro(PHYTOPLANKTON_NAME), CHILDREN_TEXT, PHYTOPLANKTON_INFO,
                intro(KRILL_NAME), CHILDREN_TEXT, KRILL_INFO,
                intro(BLUE_WHALE_NAME), CHILDREN_TEXT, BLUE_WHALE_INFO])

        result = Executer.execute(gradleHome, new File(waterDir, BLUE_WHALE_NAME).absolutePath, [taskName])
        assert result.output == list2text([intro(WATER_NAME), WATER_INFO,
                intro(PHYTOPLANKTON_NAME), CHILDREN_TEXT, PHYTOPLANKTON_INFO,
                intro(KRILL_NAME), CHILDREN_TEXT, KRILL_INFO,
                intro(BLUE_WHALE_NAME), CHILDREN_TEXT, BLUE_WHALE_INFO])

        result = Executer.execute(gradleHome, new File(waterDir, PHYTOPLANKTON_NAME).absolutePath,
                [taskName])
        assert result.output == list2text([intro(WATER_NAME), WATER_INFO,
                intro(PHYTOPLANKTON_NAME), CHILDREN_TEXT, PHYTOPLANKTON_INFO])
    }

    static String intro(String projectName) {
        HELLO_CLAUSE + projectName
    }

    static String list2text(List list) {
        list.join(NL) + NL
    }

}