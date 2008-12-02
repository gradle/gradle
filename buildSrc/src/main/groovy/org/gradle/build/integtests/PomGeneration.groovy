/*
 * Copyright 2007-2008 the original author or authors.
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

import groovy.text.SimpleTemplateEngine
import org.apache.commons.io.FileUtils
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.XMLAssert
import org.hamcrest.Matchers
import org.junit.Assert

/**
 * @author Hans Dockter
 */
class PomGeneration {
    static void execute(String gradleHome, String samplesDirName) {
        File pomProjectDir = new File(samplesDirName, 'pomGeneration')
        File repoDir = new File(pomProjectDir, "pomRepo");
        FileUtils.deleteQuietly(repoDir)
        checkWithNoCustomVersion(gradleHome, pomProjectDir, repoDir);
        checkWithCustomVersion(gradleHome, pomProjectDir, repoDir);
    }

    private static def checkWithNoCustomVersion(String gradleHome, File pomProjectDir, File repoDir) {
        String version = '1.0'
        String groupId = "gradle"
        long start = System.currentTimeMillis();
        Executer.execute(gradleHome, pomProjectDir.absolutePath, ['clean', 'uploadLibs'], [], '', Executer.DEBUG)
        String repoPath = repoPath(groupId, version)
        compareXmlWithIgnoringOrder(expectedPom(version, groupId),
                pomFile(repoDir, repoPath, version).text)
        Assert.assertTrue(new File(repoDir, "$repoPath/mywar-${version}.war").exists())
        checkInstall(start, pomProjectDir, version, groupId)
    }

    private static def checkWithCustomVersion(String gradleHome, File pomProjectDir, File repoDir) {
        long start = System.currentTimeMillis();
        String version = "1.0MVN"
        String groupId = "deployGroup"
        Executer.execute(gradleHome, pomProjectDir.absolutePath, ["-PcustomVersion=${version} clean", 'uploadLibs'], [], '', Executer.DEBUG)
        String repoPath = repoPath(groupId, version)
        compareXmlWithIgnoringOrder(expectedPom(version, groupId),
                pomFile(repoDir, repoPath, version).text)
        Assert.assertTrue(new File(repoDir, "$repoPath/mywar-${version}.war").exists())
        checkInstall(start, pomProjectDir, version, groupId)
    }

    static String repoPath(String group, String version) {
        "$group/mywar/$version"
    }

    static File pomFile(File repoDir, String repoPath, String version) {
        new File(repoDir, "$repoPath/mywar-${version}.pom")
    }

    static void checkInstall(long start, File pomProjectDir, String version, String groupId) {
        File localMavenRepo = new File(pomProjectDir, "build/localRepoPath.txt").text as File
        File installedFile = new File(localMavenRepo, "$groupId/mywar/$version/mywar-${version}.war")
        File installedPom = new File(localMavenRepo, "$groupId/mywar/$version/mywar-${version}.pom")
        Assert.assertTrue(start <= installedFile.lastModified());
        compareXmlWithIgnoringOrder(expectedPom(version, groupId), installedPom.text)
    }

    private static String expectedPom(String version, String groupId) {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        templateEngine.createTemplate(PomGeneration.getResourceAsStream('pomGeneration/expectedPom.txt').text).make(version: version, groupId: groupId)
    }

    private static void compareXmlWithIgnoringOrder(String expectedXml, String actualXml) {
        Diff diff = new Diff(expectedXml, actualXml)
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier())
        XMLAssert.assertXMLEqual(diff, true);
        Assert.assertThat(actualXml, Matchers.startsWith(String.format('<?xml version="1.0" encoding="UTF-8"?>%n<!-- mylicenseheader -->')))
    }

    static void main(String[] args) {
        execute(args[0], args[1])
    }


}
