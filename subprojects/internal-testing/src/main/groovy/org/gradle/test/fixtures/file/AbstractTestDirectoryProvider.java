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

import groovy.lang.Closure;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.test.fixtures.ConcurrentTestUtil;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.util.Random;
import java.util.regex.Pattern;


/**
 * A JUnit rule which provides a unique temporary folder for the test.
 */
abstract class AbstractTestDirectoryProvider implements TestRule, TestDirectoryProvider {
    protected TestFile root;

    private static final Random RANDOM = new Random();
    private static final int ALL_DIGITS_AND_LETTERS_RADIX = 36;
    private static final int MAX_RANDOM_PART_VALUE = Integer.valueOf("zzzzz", ALL_DIGITS_AND_LETTERS_RADIX);
    private static final Pattern WINDOWS_RESERVED_NAMES = Pattern.compile("(con)|(prn)|(aux)|(nul)|(com\\d)|(lpt\\d)", Pattern.CASE_INSENSITIVE);

    private TestFile dir;
    private String prefix;
    private boolean cleanup = true;
    private boolean suppressCleanupErrors;

    private String determinePrefix() {
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().endsWith("Test") || element.getClassName().endsWith("Spec")) {
                return StringUtils.substringAfterLast(element.getClassName(), ".") + "/unknown-test";
            }
        }
        return "unknown-test-class";
    }

    @Override
    public void suppressCleanup() {
        cleanup = false;
    }

    public boolean isCleanup() {
        return cleanup;
    }

    public Statement apply(final Statement base, Description description) {
        Class<?> testClass = description.getTestClass();
        init(description.getMethodName(), testClass.getSimpleName());

        suppressCleanupErrors = testClass.getAnnotation(LeaksFileHandles.class) != null
            || description.getAnnotation(LeaksFileHandles.class) != null;

        return new TestDirectoryCleaningStatement(base, description.getDisplayName());
    }

    private class TestDirectoryCleaningStatement extends Statement {
        private final Statement base;
        private final String displayName;

        public TestDirectoryCleaningStatement(Statement base, String displayName) {
            this.base = base;
            this.displayName = displayName;
        }

        @Override
        public void evaluate() throws Throwable {
            // implicitly don't clean up if this throws
            base.evaluate();

            try {
                if (cleanup && dir != null && dir.exists()) {
                    ConcurrentTestUtil.poll(new Closure(null, null) {
                        @SuppressWarnings("UnusedDeclaration")
                        void doCall() throws IOException {
                            FileUtils.forceDelete(dir);
                        }
                    });
                }
            } catch (Exception e) {
                String message = "Couldn't delete test dir for " + displayName + " (test is holding files open). "
                    + "In order to find out which files are held open you may find http://file-leak-detector.kohsuke.org/ useful.";
                if (suppressCleanupErrors) {
                    System.err.println(message);
                    e.printStackTrace(System.err);
                } else {
                    throw new GradleException(message, e);
                }
            }
        }
    }

    protected void init(String methodName, String className) {
        if (methodName == null) {
            // must be a @ClassRule; use the rule's class name instead
            methodName = getClass().getSimpleName();
        }
        if (prefix == null) {
            String safeMethodName = methodName.replaceAll("[^\\w]", "_");
            if (safeMethodName.length() > 30) {
                safeMethodName = safeMethodName.substring(0, 19) + "..." + safeMethodName.substring(safeMethodName.length() - 9);
            }
            prefix = String.format("%s/%s", className, safeMethodName);
        }
    }

    public TestFile getTestDirectory() {
        if (dir == null) {
           dir = createUniqueTestDirectory();
        }
        return dir;
    }

    private TestFile createUniqueTestDirectory() {
        while (true) {
            // Use a random prefix to avoid reusing test directories
            String randomPrefix = Integer.toString(RANDOM.nextInt(MAX_RANDOM_PART_VALUE), ALL_DIGITS_AND_LETTERS_RADIX);
            if (WINDOWS_RESERVED_NAMES.matcher(randomPrefix).matches()) {
                continue;
            }
            TestFile dir = root.file(getPrefix(), randomPrefix);
            if (dir.mkdirs()) {
                return dir;
            }
        }
    }

    private String getPrefix() {
        if (prefix == null) {
            // This can happen if this is used in a constructor or a @Before method. It also happens when using
            // @RunWith(SomeRunner) when the runner does not support rules.
            prefix = determinePrefix();
        }
        return prefix;
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
