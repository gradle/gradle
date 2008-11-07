package org.gradle.execution;

public class MergingBuildExecuter extends DelegatingBuildExecuter {
    public MergingBuildExecuter(BuildExecuter delegate) {
        super(delegate);
    }

    @Override
    public boolean requiresProjectReload() {
        return false;
    }
}
