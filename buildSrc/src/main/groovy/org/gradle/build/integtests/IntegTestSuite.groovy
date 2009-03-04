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
    static void execute(File distDir, File samplesDir, File userguideOutputDir, File userguideDir) {
        String distDirPath = distDir.absolutePath
        String samplesDirPath = samplesDir.absolutePath
        Userguide.execute(distDir, samplesDir, userguideOutputDir, userguideDir)
        CacheProject.execute(distDirPath, samplesDirPath)
        CommandLine.execute(distDirPath, samplesDirPath)
        WrapperProject.execute(distDirPath, samplesDirPath)
        JavaProject.execute(distDirPath, samplesDirPath)
        GroovyProject.execute(distDirPath, samplesDirPath)
        WebProject.execute(distDirPath, samplesDirPath)
        Logging.execute(distDirPath, samplesDirPath)
        Osgi.execute(distDirPath, samplesDirPath)
        PomGeneration.execute(distDirPath, samplesDirPath)
        WaterProject.execute(distDirPath, samplesDirPath)
        org.gradle.build.integtests.Version.execute(distDirPath)
        MavenRepo.execute(distDirPath, samplesDirPath)
        deleteGradleDirs(samplesDirPath)
    }

    private static void deleteGradleDirs(String samplesDirPath) {
        AntBuilder ant = new AntBuilder()
        ant.delete(includeEmptyDirs: "true") {
            fileset(dir: samplesDirPath, includes: "**/.gradle/")
        }
    }
}
