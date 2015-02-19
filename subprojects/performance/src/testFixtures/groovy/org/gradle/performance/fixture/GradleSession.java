package org.gradle.performance.fixture;

public interface GradleSession {

    public abstract GradleInvocationSpec getInvocation();

    public abstract void prepare();

    public abstract void run();

    public abstract void cleanup();

}
