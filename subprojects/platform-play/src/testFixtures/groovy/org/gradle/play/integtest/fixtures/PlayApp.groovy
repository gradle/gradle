/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.integtest.fixtures

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.RelativePathUtil

import static org.gradle.play.integtest.fixtures.Repositories.PLAY_REPOSITORIES

abstract class PlayApp {

    String getName() {
        getClass().getSimpleName().toLowerCase()
    }

    List<SourceFile> getAllFiles() {
        return appSources + testSources + viewSources + assetSources + confSources + otherSources
    }

    SourceFile getGradleBuild() {
        def gradleBuild = sourceFile("", "build.gradle")
        def gradleBuildWithRepositories = gradleBuild.content.concat """
            allprojects {
                ${PLAY_REPOSITORIES}
            }
        """
        return new SourceFile(gradleBuild.path, gradleBuild.name, gradleBuildWithRepositories)
    }

    List<SourceFile> getAssetSources() {
        sourceFiles("public", "shared")
    }

    List<SourceFile> getAppSources() {
        return sourceFiles("app").findAll {
            it.path != "app/views"
        }
    }

    List<SourceFile> getViewSources() {
        return sourceFiles("app/views");
    }

    List<SourceFile> getConfSources() {
        return sourceFiles("conf", "shared") + sourceFiles("conf")
    }

    List<SourceFile> getTestSources() {
        return sourceFiles("test")
    }

    List<SourceFile> getOtherSources() {
        return [ sourceFile("", "README", "shared") ]
    }


    protected SourceFile sourceFile(String path, String name, String baseDir = getName()) {
        URL resource = getClass().getResource("$baseDir/$path/$name");
        File file = new File(resource.toURI())
        return new SourceFile(path, name, file.text);
    }

    void writeSources(TestFile sourceDir) {
        gradleBuild.writeToDir(sourceDir)
        for (SourceFile srcFile : allFiles) {
            srcFile.writeToDir(sourceDir)
        }
    }

    List<SourceFile> sourceFiles(String baseDir, String rootDir = getName()) {
        List sourceFiles = new ArrayList()

        URL resource = getClass().getResource("$rootDir/$baseDir")
        if(resource != null){
            File baseDirFile = new File(resource.toURI())
            baseDirFile.eachFileRecurse { File source ->
                if(!source.isDirectory()){
                    String fileName = source.getName()
                    def subpath = RelativePathUtil.relativePath(baseDirFile, source.parentFile);
                    sourceFiles.add(sourceFile("$baseDir/$subpath", fileName, rootDir))
                }
            }
        }

        return sourceFiles
    }
}
