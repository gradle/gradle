package org.gradle.api.internal.project.antbuilder;

import org.gradle.internal.classpath.ClassPath;

public class ClassPathToClassLoader {
    private final static String ISOLATED_ANT_CLASS_LOADER = "Isolated Ant Classpath";

    private ClassLoader classLoader;
    private MemoryLeakPrevention classLoaderLeakPrevention;
    private final MemoryLeakPrevention gradleToIsolatedLeakPrevention;
    private final MemoryLeakPrevention antToIsolatedLeakPrevention;

    public ClassPathToClassLoader(ClassPath classPath, ClassLoader classLoader,
                                  MemoryLeakPrevention gradleToIsolatedLeakPrevention,
                                  MemoryLeakPrevention antToIsolatedLeakPrevention) {
        this.classLoader = classLoader;
        this.gradleToIsolatedLeakPrevention = gradleToIsolatedLeakPrevention;
        this.antToIsolatedLeakPrevention = antToIsolatedLeakPrevention;
        this.classLoaderLeakPrevention = new MemoryLeakPrevention(ISOLATED_ANT_CLASS_LOADER, classLoader, classPath);
        this.classLoaderLeakPrevention.prepare();
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public MemoryLeakPrevention getClassLoaderLeakPrevention() {
        return classLoaderLeakPrevention;
    }

    public void cleanup() {
        // clean classes from the isolated builder which leak into the various loaders
        classLoaderLeakPrevention.dispose(classLoader, antToIsolatedLeakPrevention.getLeakingLoader(),  this.getClass().getClassLoader());

        // clean classes from the Gradle Core loader which leaked into the isolated builder and Ant loader
        gradleToIsolatedLeakPrevention.dispose(classLoader);

        // clean classes from the Gradle "ant" loader which leaked into the isolated builder
        antToIsolatedLeakPrevention.dispose(classLoader);


        classLoader = null;
        classLoaderLeakPrevention = null;
    }


}
