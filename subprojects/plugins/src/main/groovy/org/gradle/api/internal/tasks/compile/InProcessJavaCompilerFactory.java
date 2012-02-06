package org.gradle.api.internal.tasks.compile;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.util.Jvm;
import org.gradle.util.ReflectionUtil;

public class InProcessJavaCompilerFactory implements JavaCompilerFactory {
    private static final boolean SUN_COMPILER_AVAILABLE = ReflectionUtil.isClassAvailable("com.sun.tools.javac.Main");

    public JavaCompiler create(CompileOptions options) {
        if (Jvm.current().isJava6Compatible()) {
            return createJdk6Compiler();
        }
        if (SUN_COMPILER_AVAILABLE) {
            return new SunCompilerFactory().create();
        }
        throw new RuntimeException("Cannot find a Java compiler API. Please let us know which JDK/platform you are using. To work around this problem, try 'compileJava.options.useAnt=true'.");
    }

    private JavaCompiler createJdk6Compiler() {
        try {
            // excluded when Gradle is compiled against JDK5, hence we can't reference it statically
            Class<?> clazz = getClass().getClassLoader().loadClass("org.gradle.api.internal.tasks.compile.jdk6.Jdk6JavaCompiler");
            return (JavaCompiler) clazz.newInstance();
        } catch (Exception e) {
            throw new GradleException("Internal error: couldn't load or instantiate class Jdk6JavaCompiler", e);
        }
    }

    // nested class to enforce lazy class loading
    private static class SunCompilerFactory {
        JavaCompiler create() {
            return new SunJavaCompiler();
        }
    }
}
