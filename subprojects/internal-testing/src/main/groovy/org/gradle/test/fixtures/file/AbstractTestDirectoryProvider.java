/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.test.fixtures.file;

import org.apache.commons.lang.StringUtils;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.util.Random;


/**
 * A JUnit rule which provides a unique temporary folder for the test.
 */
abstract class AbstractTestDirectoryProvider implements MethodRule, TestRule, TestDirectoryProvider {
    private TestFile dir;
    private String prefix;
    protected static TestFile root;
    private static final Random RANDOM = new Random();
    public static final int ALL_DIGITS_AND_LETTERS_RADIX = 36;
    private static final int MAX_RANDOM_PART_VALUE = Integer.valueOf("zzzzz", ALL_DIGITS_AND_LETTERS_RADIX);

    private String determinePrefix() {
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().endsWith("Test") || element.getClassName().endsWith("Spec")) {
                return StringUtils.substringAfterLast(element.getClassName(), ".") + "/unknown-test";
            }
        }
        return "unknown-test-class";
    }

    protected Statement doApply(final Statement base, FrameworkMethod method, Object target) {
        init(method.getName(), target.getClass().getSimpleName());
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
                getTestDirectory().maybeDeleteDir();
                // Don't delete on failure
            }
        };
    }

    public Statement apply(final Statement base, Description description) {
        init(description.getMethodName(), description.getTestClass().getSimpleName());
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
                getTestDirectory().deleteDir();
                // Don't delete on failure
            }
        };
    }

    protected void init(String methodName, String className) {
        if (methodName == null) {
            // must be a @ClassRule; use the rule's class name instead
            methodName = getClass().getSimpleName();
        }
        if (prefix == null) {
            String safeMethodName = methodName.replaceAll("\\s", "_").replace(File.pathSeparator, "_").replace(":", "_").replace('"', '_');
            if (safeMethodName.length() > 60) {
                safeMethodName = safeMethodName.substring(0, 29) + "..." + safeMethodName.substring(safeMethodName.length() - 29);
            }
            prefix = String.format("%s/%s", className, safeMethodName);
        }
    }

    public TestFile getTestDirectory() {
        if (dir == null) {
            if (prefix == null) {
                // This can happen if this is used in a constructor or a @Before method. It also happens when using
                // @RunWith(SomeRunner) when the runner does not support rules.
                prefix = determinePrefix();
            }
            while (true) {
                // Use a random prefix to avoid reusing test directories
                dir = root.file(prefix, Integer.toString(RANDOM.nextInt(MAX_RANDOM_PART_VALUE), ALL_DIGITS_AND_LETTERS_RADIX));
                if (dir.mkdirs()) {
                    break;
                }
            }
        }
        return dir;
    }

    public TestFile file(Object... path) {
        return getTestDirectory().file((Object[]) path);
    }

    public TestFile createFile(Object... path) {
        return file((Object[]) path).createFile();
    }

    public TestFile createDir(Object... path) {
        return file((Object[]) path).createDir();
    }
}