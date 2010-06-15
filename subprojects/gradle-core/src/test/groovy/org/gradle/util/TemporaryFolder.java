/*
 * Copyright 2010 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;

/**
 * A JUnit rule which provides a unique temporary folder for the test.
 */
public class TemporaryFolder implements MethodRule, TestFileContext {
    private TestFile dir;
    private String prefix;
    private static TestFile root;

    static {
        root = new TestFile(new File("build/tmp/tests"));
    }

    public TestFile getDir() {
        if (dir == null) {
            if (prefix == null) {
                // This can happen if this is used in a constructor or a @Before method. It also happens when using
                // @RunWith(SomeRunner) when the runner does not support rules.
                prefix = determinePrefix();
            }
            for (int counter = 1; true; counter++) {
                dir = root.file(counter == 1 ? prefix : String.format("%s%d", prefix, counter));
                if (dir.mkdirs()) {
                    break;
                }
            }
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
        init(method, target);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
                getDir().maybeDeleteDir();
                // Don't delete on failure
            }
        };
    }

    private void init(FrameworkMethod method, Object target) {
        if (prefix == null) {
            prefix = String.format("%s/%s", target.getClass().getSimpleName(), method.getName());
        }
    }

    public static TemporaryFolder newInstance() {
        return new TemporaryFolder();
    }

    public static TemporaryFolder newInstance(FrameworkMethod method, Object target) {
        TemporaryFolder temporaryFolder = new TemporaryFolder();
        temporaryFolder.init(method, target);
        return temporaryFolder;
    }

    public TestFile getTestDir() {
        return getDir();
    }

    public TestFile file(Object... path) {
        return getDir().file((Object[]) path);
    }

    public TestFile createFile(Object... path) {
        return file((Object[]) path).createFile();
    }

    public TestFile createDir(Object... path) {
        return file((Object[]) path).createDir();
    }
}