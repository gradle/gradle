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

import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.XMLAssert
import org.apache.commons.io.IOUtils
import org.apache.commons.io.FileUtils;

/**
 * @author Hans Dockter
 */
class PomGeneration {
    static void execute(String gradleHome, String samplesDirName) {
        File pomprojectDir = new File(samplesDirName, 'pomGeneration')
        File pomFile = new File(pomprojectDir, "pomRepo/pom.xml");
        FileUtils.deleteQuietly(pomFile.parentFile)
        pomFile.parentFile.mkdirs()
        Executer.execute(gradleHome, pomprojectDir.absolutePath, ['clean', 'uploadLibs'], [], '', Executer.DEBUG)
        compareXmlWithIgnoringOrder(JavaProject.getResourceAsStream("pomGeneration/expectedPom.txt").text,
              pomFile.text)
    }

    private static void compareXmlWithIgnoringOrder(String expectedXml, String actualXml) {
        Diff diff = new Diff(expectedXml, actualXml)
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier())
        XMLAssert.assertXMLEqual(diff, true);
    }

    static void main(String[] args) {
        execute(args[0], args[1])
    }    
}
