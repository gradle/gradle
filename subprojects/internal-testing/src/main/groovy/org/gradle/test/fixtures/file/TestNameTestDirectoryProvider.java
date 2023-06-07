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
package org.gradle.test.fixtures.file;

import org.gradle.internal.os.OperatingSystem;
import org.junit.runners.model.FrameworkMethod;

import java.io.File;

/**
 * A JUnit rule which provides a unique temporary folder for the test.
 */
public class TestNameTestDirectoryProvider extends AbstractTestDirectoryProvider {
    public TestNameTestDirectoryProvider(Class<?> klass) {
        super(new TestFile(new File("build/tmp/" + determineTestDirectoryName(klass))), klass);
    }

    public TestNameTestDirectoryProvider(TestFile root, Class<?> klass) {
        super(root, klass);
    }

    private static String determineTestDirectoryName(Class<?> klass) {
        // NOTE: the space in the directory name is intentional to shake out problems with paths that contain spaces
        // NOTE: and so is the "s with circumflex" character (U+015D), to shake out problems with non-ASCII folder names
        return shouldUseNonAsciiPath(klass)
            ? "teŝt files"
            : "test files";
    }

    private static boolean shouldUseNonAsciiPath(Class<?> klass) {
        // TODO Remove this parameter and fix encoding problems on Windows, too
        return !OperatingSystem.current().isWindows()
            && !klass.isAnnotationPresent(DoesNotSupportNonAsciiPaths.class);
    }

    public static TestNameTestDirectoryProvider forFatDrive(Class<?> klass) {
        return new TestNameTestDirectoryProvider(new TestFile(new File("D:\\tmp\\test-files")), klass);
    }

    public static TestNameTestDirectoryProvider newInstance(Class<?> testClass) {
        return new TestNameTestDirectoryProvider(testClass);
    }

    public static TestNameTestDirectoryProvider newInstance(FrameworkMethod method, Object target) {
        TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(target.getClass());
        testDirectoryProvider.init(method.getName());
        return testDirectoryProvider;
    }
}
