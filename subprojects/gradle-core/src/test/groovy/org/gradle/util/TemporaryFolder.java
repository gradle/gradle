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
package org.gradle.util;

import org.gradle.integtests.TestFile;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.apache.commons.lang.StringUtils;

import java.io.File;

public class TemporaryFolder implements MethodRule {
    private TestFile dir;
    private boolean initialised;
    private static TestFile root;

    static {
        root = new TestFile(new File("build/tmp/tests"));
    }

    public TestFile getDir() {
        if (!initialised) {
            if (dir == null) {
                // This can happen if this is used in a constructor or a @Before method. It also happens when using
                // @RunWith(SomeRunner) when the runner does not support rules.
                String prefix = determinePrefix();
                dir = root.file(prefix);
            }
            GradleUtil.deleteDir(dir);
            dir.mkdirs();
            initialised = true;
        }
        return dir;
    }

    private String determinePrefix() {
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().endsWith("Test")) {
                return StringUtils.substringAfterLast(element.getClassName(), ".") + "/unknown-test";
            }
        }
        return "unknown-test-class";
    }

    public Statement apply(final Statement base, final FrameworkMethod method, final Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (dir == null) {
                    dir = root.file(target.getClass().getSimpleName(), method.getName());
                }
                base.evaluate();
            }
        };
    }

    public TestFile file(String... path) {
        return getDir().file((Object[])path).touch();
    }

    public TestFile dir(String... path) {
        TestFile dir = getDir().file((Object[])path);
        dir.mkdirs();
        return dir;
    }
}