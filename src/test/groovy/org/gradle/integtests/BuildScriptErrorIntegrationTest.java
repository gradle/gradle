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
package org.gradle.integtests;

import org.gradle.Build;
import org.gradle.StartParameter;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Project;
import org.hamcrest.Matchers;
import static org.junit.Assert.assertThat;
import org.junit.Ignore;
import org.junit.Test;

public class BuildScriptErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void lineNumberOutputForCompileError() {
        checkLineInfo("createTakk('do-stuff')", startParameter());
    }

    @Ignore
    public void lineNumberOutputForRuntimeError() {
        // todo We need to figure when the Groovy compile provided line info and when not. I can't easily produce a runtime
        // error with line info information although I know there are runtime errors with line info. 
        String script = "createTask('do-stuff') { 1 / 0 }";
        checkLineInfo(script, startParameter("do-stuff"));
    }

    private void checkLineInfo(String script, StartParameter parameter) {
        parameter.setBuildFileName(Project.EMBEDDED_SCRIPT_ID);
        try {
            Build.newInstanceFactory(parameter).newInstance(script, null).runNonRecursivelyWithCurrentDirAsRoot(parameter);
        } catch (GradleScriptException e) {
            assertThat(e.getMessage(), Matchers.containsString("line(s): 1"));
        }
    }
}