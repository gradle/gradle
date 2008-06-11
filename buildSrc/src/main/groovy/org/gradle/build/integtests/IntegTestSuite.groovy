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
class IntegTestSuite {
    static void execute(String distDirPath, String samplesDirPath, String userguideOutputDir) {
        WrapperProject.execute(distDirPath, samplesDirPath)
        JavaProject.execute(distDirPath, samplesDirPath)
        GroovyProject.execute(distDirPath, samplesDirPath)
        WaterProject.execute(distDirPath, samplesDirPath)
        PropertiesTest.execute(distDirPath, samplesDirPath, userguideOutputDir)
        org.gradle.build.integtests.Version.execute(distDirPath)
        Userguide.execute(distDirPath, samplesDirPath, userguideOutputDir)
        MavenRepo.execute(distDirPath, samplesDirPath)
    }
}
