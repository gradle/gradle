package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.incap.BuildSpec;
import org.gradle.incap.BuildWorkflow;
import org.gradle.incap.Incap;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Notifies INCAP library of new builds.
 */
public class IncrementalAnnotationsSupport {

    JavaCompileSpec spec;

    public IncrementalAnnotationsSupport(JavaCompileSpec spec) {
        this.spec = spec;
    }

    void prebuildFull() {
        if (!spec.getAnnotationProcessorPath().isEmpty()) {
            Incap.getBuildWorkflow().preBuild(new BuildSpec.Builder()
                .setBuildType(BuildSpec.BuildType.FULL)
                .setClassPath(spec.getCompileClasspath())
                .setWorkingDir(spec.getWorkingDir()).build());
        }
    }

    public BuildWorkflow.PreBuildResult prebuildIncremental(RecompilationSpec recompileSpec) {
        Set<String> changed = new HashSet<String>(recompileSpec.getClassNames().size());
        changed.addAll(recompileSpec.getClassNames());

        return Incap.getBuildWorkflow().preBuild(new BuildSpec.Builder()
            .setBuildType(BuildSpec.BuildType.INCREMENTAL)
            .setClassPath(spec.getCompileClasspath())
            .setWorkingDir(spec.getWorkingDir())
            .setModifiedClasses(changed)
            // TODO:  need to pass classes that were deleted before this build.
            // All we have are deleted files.  Can we get them from the StaleClassCleaner?
            // Or do we reconstruct them from the IncrementalClassInputs?
            // Or, do we pass deleted files to Incap, and it reconstructs their names? (yuck)
            .build());
    }

    public BuildWorkflow.PostBuildResult postBuild() {
        try {
            return Incap.getBuildWorkflow().postBuild();
        } catch (IOException iox) {
            // TODO:  Do we really want to have the API throw an exception here?
            throw new RuntimeException(iox);  // punt for now
        }
    }
}
