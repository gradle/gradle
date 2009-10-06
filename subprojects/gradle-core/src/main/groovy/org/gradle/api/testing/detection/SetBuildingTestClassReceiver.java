package org.gradle.api.testing.detection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Tom Eyckmans
 */
public class SetBuildingTestClassReceiver implements TestClassReceiver {

    private final Set<String> testClassNames;

    public SetBuildingTestClassReceiver(final Set<String> testClassNames) {
        this.testClassNames = new HashSet<String>(testClassNames);
    }

    public SetBuildingTestClassReceiver() {
        this.testClassNames = new HashSet<String>();
    }

    public void receiveTestClass(final String testClassName) {
        testClassNames.add(testClassName);
    }

    public Set<String> getTestClassNames() {
        return Collections.unmodifiableSet(testClassNames);
    }
}
