/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.util.TestFile
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
@RunWith (DistributionIntegrationTestRunner.class)
class UserguideIntegrationTest {

    private static Logger logger = LoggerFactory.getLogger(UserguideIntegrationTest)

    @Rule public final GradleDistribution dist = new GradleDistribution()
    private final GradleExecuter executer = dist.executer

    @Test
    public void apiLinks() {
        TestFile gradleHome = dist.gradleHomeDir         
        TestFile userguideInfoDir = dist.userGuideInfoDir
        Node links = new XmlParser().parse(new File(userguideInfoDir, 'links.xml'))
        links.children().each {Node link ->
            String classname = link.'@className'
            String lang = link.'@lang'
            File classDocFile = new File(gradleHome, "docs/${lang}doc/${classname.replace('.', '/')}.html")
            Assert.assertTrue("Could not find javadoc for class '$classname' referenced in userguide: $classDocFile does not exist.", classDocFile.isFile())
        }
    }
}
