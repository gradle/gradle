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

import org.gradle.build.samples.WaterProjectCreator

/**
 * @author Hans Dockter
 */
class WaterProject {

   static void execute(String gradleHome, String samplesDirName) {
        File waterDir = new File(samplesDirName, WaterProjectCreator.WATER_NAME)
        String taskName = 'hello'
        String output = Executer.execute(gradleHome, waterDir.absolutePath, [taskName])
        assert output == list2text([intro(WaterProjectCreator.WATER_NAME), WaterProjectCreator.WATER_INFO, 
                intro(WaterProjectCreator.PHYTOPLANKTON_NAME), WaterProjectCreator.CHILDREN_TEXT, WaterProjectCreator.PHYTOPLANKTON_INFO,
                intro(WaterProjectCreator.KRILL_NAME), WaterProjectCreator.CHILDREN_TEXT, WaterProjectCreator.KRILL_INFO,
                intro(WaterProjectCreator.BLUE_WHALE_NAME), WaterProjectCreator.CHILDREN_TEXT, WaterProjectCreator.BLUE_WHALE_INFO])

        output = Executer.execute(gradleHome, new File(waterDir, WaterProjectCreator.BLUE_WHALE_NAME).absolutePath, [taskName])
        assert output == list2text([intro(WaterProjectCreator.WATER_NAME), WaterProjectCreator.WATER_INFO,
                intro(WaterProjectCreator.PHYTOPLANKTON_NAME), WaterProjectCreator.CHILDREN_TEXT, WaterProjectCreator.PHYTOPLANKTON_INFO,
                intro(WaterProjectCreator.KRILL_NAME), WaterProjectCreator.CHILDREN_TEXT, WaterProjectCreator.KRILL_INFO,
                intro(WaterProjectCreator.BLUE_WHALE_NAME), WaterProjectCreator.CHILDREN_TEXT, WaterProjectCreator.BLUE_WHALE_INFO])

        output = Executer.execute(gradleHome, new File(waterDir, WaterProjectCreator.PHYTOPLANKTON_NAME).absolutePath,
                [taskName])
        assert output == list2text([intro(WaterProjectCreator.WATER_NAME), WaterProjectCreator.WATER_INFO,
                intro(WaterProjectCreator.PHYTOPLANKTON_NAME), WaterProjectCreator.CHILDREN_TEXT, WaterProjectCreator.PHYTOPLANKTON_INFO])
    }

    static String intro(String projectName) {
        WaterProjectCreator.HELLO_CLAUSE + projectName
    }

    static String list2text(List list) {
        list.join(WaterProjectCreator.NL) + WaterProjectCreator.NL
    }

}