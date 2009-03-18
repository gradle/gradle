package org.gradle.execution;

import org.gradle.api.Project;

public class DelegatingBuildExecuter implements BuildExecuter {
    private BuildExecuter delegate;

    public DelegatingBuildExecuter(BuildExecuter delegate) {
        this.delegate = delegate;
    }

    public DelegatingBuildExecuter() {
    }

    protected BuildExecuter getDelegate() {
        return delegate;
    }

    protected void setDelegate(BuildExecuter delegate) {
        this.delegate = delegate;
    }

    public void select(Project project) {
        delegate.select(project);
    }

    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    public void execute(TaskExecuter executer) {
        delegate.execute(executer);
    }
}
