package org.gradle;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;

public class BrokenRunner extends Runner {
    private final Class<?> type;

    public BrokenRunner(Class<?> type) {
        this.type = type;
    }

    @Override
    public Description getDescription() {
        return Description.createSuiteDescription(type);
    }

    @Override
    public void run(RunNotifier notifier) {
        throw new UnsupportedOperationException("broken");
    }
}
