package org.gradle

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

public class GradleActivator implements BundleActivator {

    public void start(BundleContext context) {
        println "Hello from a Groovy Gradle Activator"
    }

    public void stop(BundleContext context) { 
    }
}
