/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.migration;

import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.migration.model.association.BuildFunctionAssociation;
import org.gradle.api.plugins.migration.model.compare.BuildFunctionComparator;
import org.gradle.api.plugins.migration.model.compare.BuildFunctionComparisonRenderer;
import org.gradle.api.plugins.migration.model.function.BuildFunctionSet;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.io.Writer;
import java.util.Set;
import java.util.concurrent.Callable;

public class CompareBuildFunctions extends DefaultTask {

    BuildFunctionSet left;

    BuildFunctionSet right;

    // items from left and right associated, may not be everything
    // e.g. left may have more functions than right, or there may be unmapped functions
    Set<BuildFunctionAssociation<?>> associations;

    Set<BuildFunctionComparator<?>> comparators;

    Set<BuildFunctionComparisonRenderer<?, Writer>> renderers;

    File reportDir;

    CompareBuildFunctions() {
        dependsOn(new Callable<TaskDependency>() {
            public TaskDependency call() throws Exception {
                return getLeft().getBuildDependencies();
            }
        });
        dependsOn(new Callable<TaskDependency>() {
            public TaskDependency call() throws Exception {
                return getRight().getBuildDependencies();
            }
        });
    }

    public BuildFunctionSet getLeft() {
        return left;
    }

    public void setLeft(BuildFunctionSet left) {
        this.left = left;
    }

    public BuildFunctionSet getRight() {
        return right;
    }

    public void setRight(BuildFunctionSet right) {
        this.right = right;
    }

    public Set<BuildFunctionAssociation<?>> getAssociations() {
        return associations;
    }

    public void setAssociations(Set<BuildFunctionAssociation<?>> associations) {
        this.associations = associations;
    }

    public Set<BuildFunctionComparator<?>> getComparators() {
        return comparators;
    }

    public void setComparators(Set<BuildFunctionComparator<?>> comparators) {
        this.comparators = comparators;
    }

    @OutputDirectory
    public File getReportDir() {
        return reportDir;
    }

    public void setReportDir(File reportDir) {
        this.reportDir = reportDir;
    }

    public Set<BuildFunctionComparisonRenderer<?, Writer>> getRenderers() {
        return renderers;
    }

    public void setRenderers(Set<BuildFunctionComparisonRenderer<?, Writer>> renderers) {
        this.renderers = renderers;
    }
}
