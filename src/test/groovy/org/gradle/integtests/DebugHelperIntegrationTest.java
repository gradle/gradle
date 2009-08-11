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

import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.gradle.DebugHelper;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;

public class DebugHelperIntegrationTest extends AbstractIntegrationTest
{
    @Test
    public void basicDebugHelper() {
        TestFile testFile = testFile("build.gradle").writelns("task doNothing");
        inTestDirectory().withTasks("doNothing").run();

        DebugHelper debugHelper = new DebugHelper(getTestDir());
        String className = debugHelper.getClassNameForScript(testFile);
        File scriptFile = debugHelper.getScriptForClassName(className);

        assertThat(scriptFile, equalTo((File)testFile));
    }
}

