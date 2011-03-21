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

import org.gradle.integtests.fixtures.ExecutionFailure;
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest;
import org.gradle.util.TestFile;
import org.junit.Test;

import static org.hamcrest.Matchers.containsString;

public class InitScriptErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsInitScriptEvaluationFailsWithGroovyException() {
        TestFile initScript = testFile("init.gradle");
        initScript.write("\ncreateTakk('do-stuff')");
        ExecutionFailure failure = inTestDirectory().usingInitScript(initScript).runWithFailure();

        failure.assertHasFileName(String.format("Initialization script '%s'", initScript));
        failure.assertHasLineNumber(2);
        failure.assertHasDescription("A problem occurred evaluating initialization script.");
        failure.assertHasCause("No signature of method: org.gradle.invocation.DefaultGradle.createTakk() is applicable for argument types: (java.lang.String) values: [do-stuff]");
    }

    @Test
    public void reportsGroovyCompilationException() {
        TestFile initScript = testFile("init.gradle");
        initScript.writelns(
            "// a comment",
            "import org.gradle.unknown.Unknown",
            "new Unknown()");
        ExecutionFailure failure = inTestDirectory().usingInitScript(initScript).runWithFailure();
        failure.assertHasFileName(String.format("Initialization script '%s'", initScript));
        failure.assertHasLineNumber(2);
        failure.assertHasDescription(String.format("Could not compile initialization script '%s'.", initScript));
        failure.assertThatCause(containsString(String.format("initialization script '%s': 2: unable to resolve class org.gradle.unknown.Unknown", initScript)));
    }
}
