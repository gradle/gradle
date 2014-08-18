package org.gradle.language.scala.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.internal.AbstractLanguageSourceSet;
import org.gradle.language.scala.ScalaSourceSet;
import org.gradle.runtime.jvm.Classpath;

import javax.inject.Inject;

public class DefaultScalaSourceSet extends AbstractLanguageSourceSet implements ScalaSourceSet {
    private final Classpath compileClasspath;

    @Inject
    public DefaultScalaSourceSet(String name, FunctionalSourceSet parent, FileResolver fileResolver) {
        super(name, parent, "Scala source", new DefaultSourceDirectorySet("source", fileResolver));
        this.compileClasspath = new EmptyClasspath();
    }

    public DefaultScalaSourceSet(String name, SourceDirectorySet source, Classpath compileClasspath, FunctionalSourceSet parent) {
        super(name, parent, "Scala source", source);
        this.compileClasspath = compileClasspath;
    }

    public Classpath getCompileClasspath() {
        return compileClasspath;
    }
//
//    public TaskDependency getBuildDependencies() { //TODO: what is this?
//        return new TaskDependency() {
//            public Set<? extends Task> getDependencies(Task task) {
//                Set<Task> dependencies = new HashSet<Task>();
//                dependencies.addAll(compileClasspath.getBuildDependencies().getDependencies(task));
//                dependencies.addAll(getSource().getBuildDependencies().getDependencies(task));
//                return dependencies;
//            }
//        };
//    }

    // Temporary Classpath implementation for new jvm component model
    private static class EmptyClasspath implements Classpath {
        public FileCollection getFiles() {
            return new SimpleFileCollection();
        }

        public TaskDependency getBuildDependencies() {
            return new DefaultTaskDependency();
        }
    }

}
