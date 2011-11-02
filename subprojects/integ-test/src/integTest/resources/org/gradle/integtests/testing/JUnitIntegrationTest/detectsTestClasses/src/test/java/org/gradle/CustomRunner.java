package org.gradle;

import org.junit.runner.*;
import org.junit.runner.notification.RunNotifier;

public class CustomRunner extends Runner {
    private final Description description;

    public CustomRunner(Class type) throws Exception {
        description = Description.createSuiteDescription(type);
        description.addChild(Description.createTestDescription(type, "ok"));
    }

    @Override
    public Description getDescription() {
        return description;
    }

    @Override
    public void run(RunNotifier notifier) {
        for (Description child : description.getChildren()) {
            notifier.fireTestStarted(child);
            notifier.fireTestFinished(child);
        }
    }
}
