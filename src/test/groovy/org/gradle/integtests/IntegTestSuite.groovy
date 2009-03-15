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

package org.gradle.integtests

import org.gradle.integtests.testng.TestNGIntegration

/**
 * @author Hans Dockter
 */
class IntegTestSuite {
    public static void main(String[] args) {
        execute(file('integTest.gradleHomeDir'), file('integTest.samplesDir'), file('integTest.userGuideOutputDir'),
                file('integTest.userGuideInfoDir'))
    }

    static File file(String propName) {
        new File(System.getProperty(propName)).canonicalFile
    }
    
    static void execute(File distDir, File samplesDir, File userguideOutputDir, File userguideDir) {
        GradleDistribution dist = [
                getGradleHomeDir: {-> distDir},
                getSamplesDir: {-> samplesDir},
                getUserGuideInfoDir: {-> userguideDir},
                getUserGuideOutputDir: {-> userguideOutputDir}
        ] as GradleDistribution

        TestNGIntegration.execute(dist)
        Userguide.execute(dist)
        CacheProject.execute(dist)
        CommandLine.execute(dist)
        WrapperProject.execute(dist)
        JavaProject.execute(dist)
        GroovyProject.execute(dist)
        WebProject.execute(dist)
        Logging.execute(dist)
        Osgi.execute(dist)
        PomGeneration.execute(dist)
        WaterProject.execute(dist)
        Version.execute(dist)
        MavenRepo.execute(dist)
        deleteGradleDirs(samplesDir.absolutePath)
    }

    private static void deleteGradleDirs(String samplesDirPath) {
        AntBuilder ant = new AntBuilder()
        ant.delete(includeEmptyDirs: "true") {
            fileset(dir: samplesDirPath, includes: "**/.gradle/")
        }
    }
}
