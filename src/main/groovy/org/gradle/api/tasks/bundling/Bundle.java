/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.bundling;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.Resources;
import org.gradle.execution.Dag;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class Bundle extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Resources.class);

    public static final String BASENAME_KEY = "baseName";
    public static final String APPENDIX_KEY = "appendix";
    public static final String CLASSIFIER_KEY = "classifier";

    private Set childrenDependOn = new HashSet();

    private Map<String, ArchiveType> defaultArchiveTypes;

    private List<AbstractArchiveTask> archiveTasks = new ArrayList<AbstractArchiveTask>();

    private List<String> defaultConfigurations = new ArrayList<String>();

    public Bundle(Project project, String name, Dag tasksGraph) {
        super(project, name, tasksGraph);
    }

    public AbstractArchiveTask createArchive(ArchiveType type, Closure configureClosure) {
        return createArchive(type, new HashMap<String, String>(), configureClosure);
    }

    public AbstractArchiveTask createArchive(ArchiveType type, Map<String, String> args) {
        return createArchive(type, args, null);
    }

    public AbstractArchiveTask createArchive(ArchiveType type, Map<String, String> args, Closure configureClosure) {
        String taskBaseName = GUtil.elvis(args.get(BASENAME_KEY), getProject().getArchivesTaskBaseName()) +
                (args.get(APPENDIX_KEY) != null ? "_" + args.get(APPENDIX_KEY) : "");
        String classifier = args.get(CLASSIFIER_KEY) != null ? "_" + args.get(CLASSIFIER_KEY) : "";
        String taskName = taskBaseName + classifier + "_" + type.getDefaultExtension();
        AbstractArchiveTask archiveTask = (AbstractArchiveTask) getProject().createTask(WrapUtil.toMap("type", type.getTaskClass()), taskName);
        archiveTask.conventionMapping(type.getConventionMapping());
        archiveTask.setBaseName(getProject().getArchivesBaseName() + (args.get(APPENDIX_KEY) != null ? "-" + args.get(APPENDIX_KEY) : ""));
        archiveTask.setClassifier(GUtil.isTrue(classifier) ? classifier.substring(1) : "");
        setTaskDependsOn(archiveTask, getChildrenDependOn());
        archiveTask.configurations((String[]) defaultConfigurations.toArray(new String[defaultConfigurations.size()]));
        this.dependsOn(taskName);
        archiveTasks.add(archiveTask);
        if (configureClosure != null) {
            archiveTask.configure(configureClosure);
        }
        return archiveTask;
    }

    private void setTaskDependsOn(AbstractArchiveTask task, Set<Object> childrenDependOn) {
        if (GUtil.isTrue(childrenDependOn)) {
            task.dependsOn((Object[]) childrenDependOn.toArray(new Object[childrenDependOn.size()]));
        } else {
            createDependsOnForNonArchiveParentTasks(task);
        }
    }

    private void createDependsOnForNonArchiveParentTasks(AbstractArchiveTask task) {
        Set taskDependsOn = new HashSet();
        for (Object dependsOn : getDependsOn()) {
            if (!isChildArchive(dependsOn)) {
                taskDependsOn.add(dependsOn);
            }
        }
        task.dependsOn((Object[]) taskDependsOn.toArray(new Object[taskDependsOn.size()]));
    }

    private boolean isChildArchive(Object dependsOn) {
        String dependsOnPath = dependsOn.toString();
        for (AbstractArchiveTask archiveTask : archiveTasks) {
            if (archiveTask.getName().equals(dependsOnPath)) {
                return true;
            }
        }
        return false;
    }

    public Jar jar() {
        return jar(new HashMap<String, String>());
    }

    public Jar jar(Map<String, String> args) {
        return jar(args, null);
    }

    public Jar jar(Closure configureClosure) {
        return jar(new HashMap<String, String>(), configureClosure);
    }

    public Jar jar(Map<String, String> args, Closure configureClosure) {
        return (Jar) createArchive(getDefaultArchiveTypes().get(Jar.DEFAULT_EXTENSION), args, configureClosure);
    }

    public War war() {
        return war(new HashMap<String, String>());
    }

    public War war(Map<String, String> args) {
        return war(args, null);
    }

    public War war(Closure configureClosure) {
        return war(new HashMap<String, String>(), configureClosure);
    }

    public War war(Map<String, String> args, Closure configureClosure) {
        return (War) createArchive(getDefaultArchiveTypes().get(War.WAR_EXTENSION), args, configureClosure);
    }

    public Zip zip() {
        return zip(new HashMap<String, String>());
    }

    public Zip zip(Map<String, String> args) {
        return zip(args, null);
    }

    public Zip zip(Closure configureClosure) {
        return zip(new HashMap<String, String>(), configureClosure);
    }

    public Zip zip(Map<String, String> args, Closure configureClosure) {
        return (Zip) createArchive(getDefaultArchiveTypes().get(Zip.ZIP_EXTENSION), args, configureClosure);
    }

    public Tar tar() {
        return tar(new HashMap<String, String>());
    }

    public Tar tar(Map<String, String> args) {
        return tar(args, null);
    }

    public Tar tar(Closure configureClosure) {
        return tar(new HashMap<String, String>(), configureClosure);
    }

    public Tar tar(Map<String, String> args, Closure configureClosure) {
        return (Tar) createArchive(getDefaultArchiveTypes().get(Tar.TAR_EXTENSION), args, configureClosure);
    }

    public Tar tarGz() {
        return tarGz(new HashMap<String, String>());
    }

    public Tar tarGz(Map<String, String> args) {
        return tarGz(args, null);
    }

    public Tar tarGz(Closure configureClosure) {
        return tarGz(new HashMap<String, String>(), configureClosure);
    }

    public Tar tarGz(Map<String, String> args, Closure configureClosure) {
        Tar tar = (Tar) createArchive(getDefaultArchiveTypes().get(Tar.TAR_EXTENSION + Compression.GZIP.getExtension()), args, configureClosure);
        tar.setCompression(Compression.GZIP);
        return tar;
    }

    public Tar tarBzip2() {
        return tarBzip2(new HashMap<String, String>());
    }

    public Tar tarBzip2(Map<String, String> args) {
        return tarBzip2(args, null);
    }

    public Tar tarBzip2(Closure configureClosure) {
        return tarBzip2(new HashMap<String, String>(), configureClosure);
    }

    public Tar tarBzip2(Map<String, String> args, Closure configureClosure) {
        Tar tar = (Tar) createArchive(getDefaultArchiveTypes().get(Tar.TAR_EXTENSION + Compression.BZIP2.getExtension()), args, configureClosure);
        tar.setCompression(Compression.BZIP2);
        return tar;
    }

    public Set getChildrenDependOn() {
        return (Set) conv(childrenDependOn, "childrenDependOn");
    }

    public void setChildrenDependOn(Set<String> childrenDependOn) {
        this.childrenDependOn = childrenDependOn;
    }

    public Map<String, ArchiveType> getDefaultArchiveTypes() {
        return (Map<String, ArchiveType>) conv(defaultArchiveTypes, "defaultArchiveTypes");
    }

    public void setDefaultArchiveTypes(Map<String, ArchiveType> defaultArchiveTypes) {
        this.defaultArchiveTypes = defaultArchiveTypes;
    }

    /**
     * Returns a list of the archive tasks belonging to this bundle.
     */
    public List<AbstractArchiveTask> getArchiveTasks() {
        return archiveTasks;
    }

    protected void setArchiveTasks(List<AbstractArchiveTask> archiveTasks) {
        this.archiveTasks = archiveTasks;
    }

    public List<String> getDefaultConfigurations() {
        return defaultConfigurations;
    }

    public void setDefaultConfigurations(List<String> defaultConfigurations) {
        this.defaultConfigurations = defaultConfigurations;
    }
}