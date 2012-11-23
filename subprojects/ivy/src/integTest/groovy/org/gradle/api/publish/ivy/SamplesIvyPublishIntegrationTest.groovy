/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.ivy.IvyDescriptor
import org.gradle.test.fixtures.ivy.IvyHttpRepository
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.TextUtil
import org.junit.Rule

public class SamplesIvyPublishIntegrationTest extends AbstractIntegrationSpec {

    @Rule Sample sample = new Sample("ivypublish-new")
    @Rule HttpServer httpServer

    def sample() {
        given:
        httpServer.start()
        executer.inDirectory(sample.dir)

        and:
        def fileRepo = ivy(sample.dir.file("repo"))
        IvyHttpRepository httpRepo = new IvyHttpRepository(httpServer, fileRepo)
        IvyModule ivyModule = httpRepo.module("org.gradle.test", "ivypublish", "1.0")
        def uploads = file("uploads").createDir()
        ivyModule.expectPut("user1", "secret", uploads,
                "ivypublish-1.0.jar", "ivypublish-1.0.jar.sha1",
                "ivypublishSource-1.0-src.jar", "ivypublishSource-1.0-src.jar.sha1",
                "ivy-1.0.xml", "ivy-1.0.xml.sha1"
        )

        and:
        sample.dir.file("build.gradle") << """
            publishing.repositories.ivy.url = "${httpRepo.uri}"
        """

        when:
        succeeds "publish"

        then:
        def uploadedDescriptor = uploads.file("ivy-1.0.xml")
        IvyDescriptor ivy = new IvyDescriptor(uploadedDescriptor)
        ivy.artifacts.ivypublishSource.mavenAttributes.classifier == "src"
        ivy.configurations.keySet() == ['archives', 'compile', 'default', 'runtime', 'testCompile', 'testRuntime'] as Set
        ivy.dependencies.compile.assertDependsOn('junit', 'junit', '4.10')
        ivy.dependencies.compile.assertDependsOn('ivypublish', 'subproject', 'unspecified')

        def actualIvyXmlText = uploadedDescriptor.text.replaceFirst('publication="\\d+"', 'publication="«PUBLICATION-TIME-STAMP»"').trim()
        actualIvyXmlText == expectedIvyOutput
    }

    String getExpectedIvyOutput() {
        sample.dir.file("output-ivy.xml").readLines()[1..-1].join(TextUtil.getPlatformLineSeparator()).trim()
    }
}
