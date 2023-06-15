package org.gradle.performance.fixture;

import org.gradle.test.fixtures.file.AbstractTestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;

public class PerformanceTestDirectoryProvider extends AbstractTestDirectoryProvider {
    public PerformanceTestDirectoryProvider(Class<?> klass) {
        // Java does not support spaces in GC logging location
        super(new TestFile(new File("build/tmp/performance-test-files")), klass);
    }
}
