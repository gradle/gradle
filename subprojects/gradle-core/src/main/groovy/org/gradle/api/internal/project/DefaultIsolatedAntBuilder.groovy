package org.gradle.api.internal.project

import org.gradle.util.BootstrapUtil
import org.gradle.util.ClasspathUtil
import org.gradle.util.ConfigureUtil
import org.gradle.util.FilteringClassLoader
import org.gradle.util.MultiParentClassLoader
import org.gradle.logging.AntLoggingAdapter
import org.gradle.api.internal.file.ant.AntFileResource
import org.gradle.api.internal.file.ant.BaseDirSelector

class DefaultIsolatedAntBuilder implements IsolatedAntBuilder {
    private final Map<List<File>, Map<String, Object>> classloaders = [:]

    void execute(Iterable<File> classpath, Closure antClosure) {
        List<File> normalisedClasspath = classpath as List
        Map<String, Object> classloadersForPath = classloaders[normalisedClasspath]
        ClassLoader antLoader
        ClassLoader gradleLoader
        if (!classloadersForPath) {
            // Need tools.jar for compile tasks
            List<File> fullClasspath = normalisedClasspath
            File toolsJar = ClasspathUtil.toolsJar
            if (toolsJar) {
                fullClasspath += toolsJar
            }

            Closure converter = {File file -> file.toURI().toURL() }
            URL[] classpathUrls = fullClasspath.collect(converter)
            URL[] gradleCoreUrls = BootstrapUtil.gradleCoreFiles.collect(converter)

            FilteringClassLoader loggingLoader = new FilteringClassLoader(getClass().classLoader)
            loggingLoader.allowPackage('org.slf4j')

            antLoader = new URLClassLoader(classpathUrls, ClassLoader.systemClassLoader.parent)
            gradleLoader = new URLClassLoader(gradleCoreUrls, new MultiParentClassLoader(antLoader, loggingLoader))

            classloaders[normalisedClasspath] = [antLoader: antLoader, gradleLoader: gradleLoader]
        } else {
            antLoader = classloadersForPath.antLoader
            gradleLoader = classloadersForPath.gradleLoader
        }

        ClassLoader originalLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = antLoader
        try {
            Object antBuilder = antLoader.loadClass(AntBuilder.class.name).newInstance()

            Object antLogger = gradleLoader.loadClass(AntLoggingAdapter.class.name).newInstance()
            antBuilder.project.removeBuildListener(antBuilder.project.getBuildListeners()[0])
            antBuilder.project.addBuildListener(antLogger)
            antBuilder.project.addDataTypeDefinition('gradleFileResource', gradleLoader.loadClass(AntFileResource.class.name))
            antBuilder.project.addDataTypeDefinition('gradleBaseDirSelector', gradleLoader.loadClass(BaseDirSelector.class.name))

            // Ideally, we'd delegate directly to the AntBuilder, but for some reason Groovy won't invoke methodMissing()
            // on it. So, use some indirection
            Object delegate = new AntBuilderDelegate(antBuilder)
            ConfigureUtil.configure(antClosure, delegate)
        } finally {
            Thread.currentThread().contextClassLoader = originalLoader
        }
    }
}

class AntBuilderDelegate extends BuilderSupport {
    def Object builder

    def AntBuilderDelegate(builder) {
        this.builder = builder;
    }

    def propertyMissing(String name) {
        builder."$name"
    }

    protected Object doInvokeMethod(String methodName, Object name, Object args) {
    	super.doInvokeMethod(methodName, name, args)
    	return builder.lastCompletedNode
    }

    protected Object createNode(Object name) {
        builder.createNode(name)
    }

    protected Object createNode(Object name, Map attributes) {
        builder.createNode(name, attributes)
    }

    protected Object createNode(Object name, Map attributes, Object value) {
        builder.createNode(name, attributes, value)
    }

    protected Object createNode(Object name, Object value) {
        builder.createNode(name, value)
    }

    protected void setParent(Object parent, Object child) {
        builder.setParent(parent, child)
    }

    protected void nodeCompleted(Object parent, Object node) {
        builder.nodeCompleted(parent, node)
    }
}
