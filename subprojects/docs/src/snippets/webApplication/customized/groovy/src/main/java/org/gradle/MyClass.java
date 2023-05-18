package org.gradle;

public class MyClass {
    org.CompileClass compile;
    org.ProvidedCompileClass providedCompile;

    public void doSomething() throws Exception {
        Class.forName("org.RuntimeClass");
        Class.forName("org.ProvidedRuntimeClass");
    }
}
