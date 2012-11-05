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
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.util.TestFile
import org.junit.Rule

public class SamplesIvyPublishIntegrationTest extends AbstractIntegrationSpec {

    @Rule Sample sample = new Sample("ivypublish-new")

    def sample() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds "publishToRepo"

        then:
        def repo = new IvyFileRepository(new TestFile(sample.dir, "build/repo")) {
            @Override
            String getIvyFilePattern() {
                'ivy.xml'
            }

            @Override
            String getArtifactFilePattern() {
                '[artifact](.[ext])'
            }

            @Override
            String getDirPattern() {
                "[module]/[revision]"
            }
        }

        def module = repo.module("org.gradle.test", "ivypublish", "1.0")
        module.assertArtifactsPublished("ivy.xml", "ivypublish.jar", "ivypublishSource.jar")

        IvyDescriptor ivy = module.ivy
        ivy.artifacts.ivypublishSource.m.classifier == "src"
        ivy.configurations.keySet() == ['archives', 'compile', 'default', 'runtime', 'testCompile', 'testRuntime'] as Set
        ivy.dependencies.compile.assertDependsOn('junit', 'junit', '4.10')
        ivy.dependencies.compile.assertDependsOn('ivypublish', 'subproject', 'unspecified')
    }
}
