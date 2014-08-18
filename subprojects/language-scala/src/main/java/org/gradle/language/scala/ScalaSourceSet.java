package org.gradle.language.scala;


import org.gradle.api.Incubating;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.runtime.jvm.Classpath;

/**
 * A set of sources passed to the Scala compiler.
 */
@Incubating
public interface ScalaSourceSet extends LanguageSourceSet {
    Classpath getCompileClasspath();
}
