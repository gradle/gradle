package org.gradle.api.testing;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.testing.fabric.TestFramework;
import org.gradle.external.junit.JUnitTestFramework;
import org.gradle.external.testng.TestNGTestFramework;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
public class TestFrameworkRegister {
    private static final Map<String, TestFramework> testFrameworks = new ConcurrentHashMap<String, TestFramework>();

    static {
        final TestFramework junit = new JUnitTestFramework();
        final TestFramework testng = new TestNGTestFramework();

        registerTestFramework(junit);
        registerTestFramework(testng);
    }

    public static void registerTestFramework(final TestFramework testFramework) {
        if (testFramework == null) throw new IllegalArgumentException("testFramework == null!");

        final String testFrameworkId = testFramework.getId();

        if (testFrameworks.containsKey(testFrameworkId))
            throw new IllegalArgumentException("testFramework (" + testFrameworkId + ") already registered!");

        testFrameworks.put(testFrameworkId, testFramework);
    }

    public static TestFramework getTestFramework(final String testFrameworkId) {
        if (StringUtils.isEmpty(testFrameworkId)) throw new IllegalArgumentException("testFrameworkId is empty!");

        return testFrameworks.get(testFrameworkId);
    }
}
