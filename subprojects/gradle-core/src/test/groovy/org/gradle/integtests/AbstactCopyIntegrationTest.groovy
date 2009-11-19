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
package org.gradle.integtests

import org.apache.commons.io.FileUtils
import org.junit.Before

public class AbstactCopyIntegrationTest extends AbstractIntegrationTest  {
    @Before
    public void setUp() {
        ['src', 'src2'].each {
            File resourceFile;
            try {
                resourceFile = new File(getClass().getResource("copyTestResources/$it").toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException('Could not locate copy test resources');
            }

            File testSrc = testFile(it)
            FileUtils.deleteQuietly(testSrc)

            FileUtils.copyDirectory(resourceFile, testSrc)
        }
    }
}