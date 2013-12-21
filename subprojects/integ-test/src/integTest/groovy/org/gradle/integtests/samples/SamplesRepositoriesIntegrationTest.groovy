/*
 * Copyright 2009 the original author or authors.
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
 
package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertThat

class SamplesRepositoriesIntegrationTest extends AbstractIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'userguide/artifacts/defineRepository')

    @Test
    public void repositoryNotations() {
        // This test is not very strong. Its main purpose is to the for the correct syntax as we use many
        // code snippets from this build script in the user's guide.
        File projectDir = sample.dir
        String output = executer.inDirectory(projectDir).withQuietLogging().withTasks('lookup').run().getOutput()
        assertThat(output, equalTo(String.format("localRepository%nlocalRepository%n")))
    }
}
