package org.gradle.api.tasks.bundling;

import groovy.lang.Closure;
import org.gradle.api.tasks.ParallelizableTask;

/**
 * Assembles a JAR archive.
 */
@ParallelizableTask
public class Jar extends org.gradle.jvm.tasks.Jar {
    @Override
    public Jar manifest(Closure<?> configureClosure) {
        super.manifest(configureClosure);
        return this;
    }

}
