/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.play.integtest.fixtures.external

import org.gradle.api.InvalidUserDataException
import org.gradle.integtests.fixtures.SourceFile
import org.gradle.play.integtest.fixtures.Repositories
import org.gradle.smoketests.AbstractSmokeTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.RelativePathUtil
import org.gradle.util.internal.VersionNumber

import java.util.stream.Collectors

abstract class PlayApp {
    boolean oldVersion
    VersionNumber playVersion

    PlayApp() {
    }

    PlayApp(VersionNumber version) {
        this.playVersion = version
        this.oldVersion = playVersion < VersionNumber.parse('2.6.0')
    }

    String getName() {
        getClass().getSimpleName().toLowerCase()
    }

    List<SourceFile> getAllFiles() {
        return appSources + testSources + viewSources + assetSources + confSources + otherSources
    }

    SourceFile getGradleBuild() {
        def buildFileName = oldVersion ? "build.gradle.old" : "build.gradle"
        def gradleBuild = sourceFile("", buildFileName)

        def buildFileContent = gradleBuild.content
        buildFileContent = buildFileContent.replace("PLUGIN_VERSION", AbstractSmokeTest.TestedVersions.playframework)
        buildFileContent = buildFileContent.replace("SCALA_VERSION", PlayMajorVersion.forPlayVersion(playVersion).getDefaultScalaPlatform())
        buildFileContent = buildFileContent.replace("PLAY_VERSION", playVersion.toString())
        buildFileContent = buildFileContent.concat """
            allprojects {
                ${Repositories.PLAY_REPOSITORIES}
            }
        """

        return new SourceFile(gradleBuild.path, "build.gradle", buildFileContent)
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
        return sourceFiles("app/views")
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
        URL resource = getClass().getResource("$baseDir/$path/$name")
        File file = new File(resource.toURI())
        return new SourceFile(path, name, file.text)
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
                if(source.isDirectory()){
                    return
                }

                def subpath = RelativePathUtil.relativePath(baseDirFile, source.parentFile)

                if(oldVersion) {
                    if(isOldVersionFile(source)) {
                        sourceFiles.add(new SourceFile("$baseDir/$subpath", source.name[0..<-4], source.text))
                    } else if (!oldVersionFileExists(source)) {
                        sourceFiles.add(new SourceFile("$baseDir/$subpath", source.name, source.text))
                    }
                } else {
                    if(!isOldVersionFile(source)) {
                        sourceFiles.add(new SourceFile("$baseDir/$subpath", source.name, source.text))
                    }
                }
            }
        }

        return sourceFiles
    }

    static boolean isOldVersionFile(File file) {
        return file.name.endsWith('.old')
    }

    static boolean oldVersionFileExists(File file) {
        return new File(file.parentFile, "${file.name}.old").exists()
    }
}

enum PlayMajorVersion {
    PLAY_2_3_X("2.3.x", "2.11", "2.10"),
    PLAY_2_4_X("2.4.x", "2.11", "2.10"),
    PLAY_2_5_X("2.5.x", "2.11"),
    PLAY_2_6_X("2.6.x", "2.12", "2.11"),
    PLAY_2_7_X("2.7.x", "2.13", "2.12", "2.11"),
    PLAY_2_8_X("2.8.x", "2.13", "2.12");

    private final String name
    private final List<String> compatibleScalaVersions

    PlayMajorVersion(String name, String... compatibleScalaVersions) {
        this.name = name
        this.compatibleScalaVersions = Arrays.asList(compatibleScalaVersions)
    }

    String getDefaultScalaPlatform() {
        return compatibleScalaVersions.get(0)
    }

    static PlayMajorVersion forPlayVersion(VersionNumber version) {
        if (version.getMajor() == 2) {
            int index = version.getMinor() - 3
            if (index < 0 || index >= values().length) {
                throw invalidVersion(version)
            }
            return values()[index]
        }
        throw invalidVersion(version)
    }

    private static InvalidUserDataException invalidVersion(VersionNumber playVersion) {
        return new InvalidUserDataException(String.format("Not a supported Play version: %s. This plugin is compatible with: [%s].",
            playVersion.toString(), Arrays.stream(values()).map(Object::toString).collect(Collectors.joining(", "))))
    }
}
