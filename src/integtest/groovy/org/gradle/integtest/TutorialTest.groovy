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

package org.gradle.integtest


/**
 * @author Hans Dockter
 */
class TutorialTest {
    static void main(String[] args) {
        String currentDirName = args[0]
        String gradleHome = args[1]
        TutorialCreator tutorialCreator = new TutorialCreator()
        Map scripts = tutorialCreator.scripts()
        scripts.each {entry ->
            String taskName = entry.value.size < 3 ? entry.key : entry.value[2]
            String output = Executer.execute(gradleHome, currentDirName, [taskName], "${entry.key}.groovy")
            entry.value[1](output)
        }
    }
}