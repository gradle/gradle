package org.gradle.api.internal.project;

import org.gradle.api.Project;

public interface ProjectInternal extends Project {
    Project evaluate();

    BuildScriptProcessor getBuildScriptProcessor();
}
