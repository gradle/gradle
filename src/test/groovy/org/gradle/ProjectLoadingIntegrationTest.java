/*
 * Copyright 2008 the original author or authors.
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
package org.gradle;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;

public class ProjectLoadingIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void handlesSimilarlyNamedBuildFilesInSameDirectory() {
        File buildFile1 = getTestBuildFile("similarly-named build.gradle");
        File buildFile2 = getTestBuildFile("similarly_named_build_gradle");
        assertEquals(buildFile1.getParentFile(), buildFile2.getParentFile());

        StartParameter parameter = startParameter(buildFile1, "build");
        Build.newInstanceFactory(parameter).newInstance(null, null).run(parameter);

        parameter = startParameter(buildFile2, "other-build");
        Build.newInstanceFactory(parameter).newInstance(null, null).run(parameter);

        parameter = startParameter(buildFile1, "build");
        Build.newInstanceFactory(parameter).newInstance(null, null).run(parameter);
    }

    @Test
    public void canProvideAnEmbeddedBuildFile() {
        StartParameter parameter = startParameter("do-stuff");
        String script = "Task task = createTask('do-stuff')";
        Build.newInstanceFactory(parameter).newInstance(script, null).runNonRecursivelyWithCurrentDirAsRoot(parameter);
    }
}
