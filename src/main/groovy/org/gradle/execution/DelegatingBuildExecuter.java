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

    public boolean hasNext() {
        return delegate.hasNext();
    }

    public void select(Project project) {
        delegate.select(project);
    }

    public String getDescription() {
        return delegate.getDescription();
    }

    public void execute(TaskExecuter executer) {
        delegate.execute(executer);
    }

    public boolean requiresProjectReload() {
        return delegate.requiresProjectReload();
    }
}
