package org.gradle.api.plugins;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;

import java.util.ArrayList;

/**
 * <p>A {@link Convention} used for the ApplicationPlugin.</p>
 */
public class ApplicationPluginConvention {
    private String applicationName;

    private String mainClassName;

    private Iterable<String> applicationDefaultJvmArgs = new ArrayList();

    private CopySpec applicationDistribution;

    private final Project project;

    public ApplicationPluginConvention(Project project) {
        this.project = project;
        applicationDistribution = project.copySpec(new Closure<Void>(this, this) {
            public void doCall(CopySpec it) {
            }

            public void doCall() {
                doCall(null);
            }

        });
    }

    /**
     * The name of the application.
     */
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * The fully qualified name of the application's main class.
     */
    public String getMainClassName() {
        return mainClassName;
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    /**
     * Array of string arguments to pass to the JVM when running the application
     */
    public Iterable<String> getApplicationDefaultJvmArgs() {
        return applicationDefaultJvmArgs;
    }

    public void setApplicationDefaultJvmArgs(Iterable<String> applicationDefaultJvmArgs) {
        this.applicationDefaultJvmArgs = applicationDefaultJvmArgs;
    }

    /**
     * <p>The specification of the contents of the distribution.</p> <p> Use this {@link CopySpec} to include extra files/resource in the application distribution. <pre autoTested=''> apply plugin:
     * 'application'
     *
     * applicationDistribution.from("some/dir") { include "*.txt" } </pre> <p> Note that the application plugin pre configures this spec to; include the contents of "{@code src/dist}", copy the
     * application start scripts into the "{@code bin}" directory, and copy the built jar and its dependencies into the "{@code lib}" directory.
     */
    public CopySpec getApplicationDistribution() {
        return applicationDistribution;
    }

    public void setApplicationDistribution(CopySpec applicationDistribution) {
        this.applicationDistribution = applicationDistribution;
    }

    public final Project getProject() {
        return project;
    }
}
