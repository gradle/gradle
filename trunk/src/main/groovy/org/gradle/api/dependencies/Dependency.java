package org.gradle.api.dependencies;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.internal.project.DefaultProject;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: hans
 * Date: Jan 23, 2008
 * Time: 8:17:28 AM
 * To change this template use File | Settings | File Templates.
 */
public interface Dependency {
    public static final String DEFAULT_CONFIGURATION = "default";
            
    DependencyDescriptor createDepencencyDescriptor();

    void setProject(DefaultProject project);

    void setConfs(Set confs);

    void initialize();

}
