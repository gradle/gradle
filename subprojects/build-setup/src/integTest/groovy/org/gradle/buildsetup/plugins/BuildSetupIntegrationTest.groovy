/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildsetup.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.test.fixtures.file.TestFile

class BuildSetupPluginIntegrationTest extends WellBehavedPluginTest {

    @Override
    String getMainTask() {
        return "setupBuild"
    }

    @Override
    String getPluginId() {
        "build-setup"
    }

    def "can be executed without existing pom"() {
        when:
        run 'setupBuild'
        then:
        assertFileTemplateIsValid(file("build.gradle"))
        assertFileTemplateIsValid(file("settings.gradle"))
        file("gradlew").assertExists()
        file("gradlew.bat").assertExists()
        file("gradle/wrapper/gradle-wrapper.jar").assertExists()
        file("gradle/wrapper/gradle-wrapper.properties").assertExists()
    }

    void assertFileTemplateIsValid(TestFile generatedFile) {
        assert generatedFile.exists()
        def generatedFileContent = generatedFile.text
        assert generatedFileContent != ""
        println generatedFileContent

        //validate http links in the template
        generatedFileContent.eachLine {
               (it =~ /http:\/\/[^\s]+/).each{ httpRef ->
                   assert getResponseCode(httpRef) == 200
               }
        }
    }

    private static int getResponseCode(String urlString) throws MalformedURLException, IOException {
        URL u = new URL(urlString);
        HttpURLConnection huc =  (HttpURLConnection)  u.openConnection();
        huc.setRequestMethod("GET");
        huc.connect();
        return huc.getResponseCode();
    }

}
