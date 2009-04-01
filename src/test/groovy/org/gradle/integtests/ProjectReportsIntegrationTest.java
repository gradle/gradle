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
package org.gradle.integtests;

import junit.framework.Assert;
import org.junit.Test;
import org.junit.Ignore;

import java.io.File;

public class ProjectReportsIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void dependencyReportWithConflicts() {
        File buildFile = getTestBuildFile("project-with-conflicts.gradle");
        getTestBuildFile("projectA-1.2-ivy.xml");
        getTestBuildFile("projectB-1.5-ivy.xml");
        getTestBuildFile("projectB-2.1.5-ivy.xml");
        testFile("projectA-1.2.jar").touch();
        testFile("projectB-1.5.jar").touch();
        testFile("projectB-2.1.5.jar").touch();

        usingBuildFile(buildFile).withDependencyList().run();
    }
}
