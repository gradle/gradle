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
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class Bundle extends ConventionTask {
    public static final String BASENAME_KEY = "baseName";
    public static final String APPENDIX_KEY = "appendix";
    public static final String CLASSIFIER_KEY = "classifier";
    public static final String CONFIGURATIONS_KEY = "confs";

    private Set childrenDependOn = new HashSet();

    private Map<String, ArchiveType> defaultArchiveTypes;

    private List<AbstractArchiveTask> archiveTasks = new ArrayList<AbstractArchiveTask>();

    private List<String> defaultConfigurations = new ArrayList<String>();

    private File defaultDestinationDir;

    public Bundle(Project project, String name) {
        super(project, name);
    }

    public AbstractArchiveTask createArchive(ArchiveType type, Closure configureClosure) {
        return createArchive(type, new HashMap<String, Object>(), configureClosure);
    }

    public AbstractArchiveTask createArchive(ArchiveType type, Map<String, Object> args) {
        return createArchive(type, args, null);
    }

    public AbstractArchiveTask createArchive(ArchiveType type, Map<String, Object> args, Closure configureClosure) {
        String taskBaseName = GUtil.elvis(args.get(BASENAME_KEY), getProject().getArchivesTaskBaseName()).toString() + (args.get(APPENDIX_KEY) != null ? "_" + args.get(APPENDIX_KEY) : "");
        String classifier = args.get(CLASSIFIER_KEY) != null ? "_" + args.get(CLASSIFIER_KEY) : "";
        String taskName = taskBaseName + classifier + "_" + type.getDefaultExtension();
        AbstractArchiveTask archiveTask = createArchiveTask(type, args, classifier, taskName);
        setTaskDependsOn(archiveTask, getChildrenDependOn());
        this.dependsOn(taskName);
        archiveTasks.add(archiveTask);
        applyConfigureClosure(configureClosure, archiveTask);
        return archiveTask;
    }

    private AbstractArchiveTask createArchiveTask(ArchiveType type, Map<String, Object> args, String classifier, String taskName) {
        AbstractArchiveTask archiveTask = getProject().getTasks().add(taskName, type.getTaskClass());
        archiveTask.conventionMapping(type.getConventionMapping());
        if (args.get(BASENAME_KEY) != null) {
            archiveTask.setBaseName(args.get(BASENAME_KEY).toString());
        }
        if (args.get(APPENDIX_KEY) != null) {
            archiveTask.setAppendix(args.get(APPENDIX_KEY).toString());
        }
        archiveTask.setClassifier(GUtil.isTrue(classifier) ? classifier.substring(1) : "");
        archiveTask.setExtension(type.getDefaultExtension());
        archiveTask.setDestinationDir(defaultDestinationDir);
        return archiveTask;
    }

    private void applyConfigureClosure(Closure configureClosure, AbstractArchiveTask archiveTask) {
        if (configureClosure != null) {
            archiveTask.configure(configureClosure);
        }
    }

    private void setTaskDependsOn(AbstractArchiveTask task, Set<Object> childrenDependOn) {
        if (GUtil.isTrue(childrenDependOn)) {
            task.dependsOn(childrenDependOn);
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
        task.dependsOn(taskDependsOn);
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
        return jar(new HashMap<String, Object>());
    }

    public Jar jar(Map<String, Object> args) {
        return jar(args, null);
    }

    public Jar jar(Closure configureClosure) {
        return jar(new HashMap<String, Object>(), configureClosure);
    }

    public Jar jar(Map<String, Object> args, Closure configureClosure) {
        return (Jar) createArchive(getDefaultArchiveTypes().get(Jar.DEFAULT_EXTENSION), args, configureClosure);
    }

    public War war() {
        return war(new HashMap<String, Object>());
    }

    public War war(Map<String, Object> args) {
        return war(args, null);
    }

    public War war(Closure configureClosure) {
        return war(new HashMap<String, Object>(), configureClosure);
    }

    public War war(Map<String, Object> args, Closure configureClosure) {
        return (War) createArchive(getDefaultArchiveTypes().get(War.WAR_EXTENSION), args, configureClosure);
    }

    public Zip zip() {
        return zip(new HashMap<String, Object>());
    }

    public Zip zip(Map<String, Object> args) {
        return zip(args, null);
    }

    public Zip zip(Closure configureClosure) {
        return zip(new HashMap<String, Object>(), configureClosure);
    }

    public Zip zip(Map<String, Object> args, Closure configureClosure) {
        return (Zip) createArchive(getDefaultArchiveTypes().get(Zip.ZIP_EXTENSION), args, configureClosure);
    }

    public Tar tar() {
        return tar(new HashMap<String, Object>());
    }

    public Tar tar(Map<String, Object> args) {
        return tar(args, null);
    }

    public Tar tar(Closure configureClosure) {
        return tar(new HashMap<String, Object>(), configureClosure);
    }

    public Tar tar(Map<String, Object> args, Closure configureClosure) {
        return (Tar) createArchive(getDefaultArchiveTypes().get(Tar.TAR_EXTENSION), args, configureClosure);
    }

    public Tar tarGz() {
        return tarGz(new HashMap<String, Object>());
    }

    public Tar tarGz(Map<String, Object> args) {
        return tarGz(args, null);
    }

    public Tar tarGz(Closure configureClosure) {
        return tarGz(new HashMap<String, Object>(), configureClosure);
    }

    public Tar tarGz(Map<String, Object> args, Closure configureClosure) {
        Tar tar = (Tar) createArchive(getDefaultArchiveTypes().get(Tar.TAR_EXTENSION + Compression.GZIP.getExtension()), args, configureClosure);
        tar.setCompression(Compression.GZIP);
        return tar;
    }

    public Tar tarBzip2() {
        return tarBzip2(new HashMap<String, Object>());
    }

    public Tar tarBzip2(Map<String, Object> args) {
        return tarBzip2(args, null);
    }

    public Tar tarBzip2(Closure configureClosure) {
        return tarBzip2(new HashMap<String, Object>(), configureClosure);
    }

    public Tar tarBzip2(Map<String, Object> args, Closure configureClosure) {
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

    public File getDefaultDestinationDir() {
        return defaultDestinationDir;
    }

    public void setDefaultDestinationDir(Object defaultDestinationDir) {
        this.defaultDestinationDir = new File(defaultDestinationDir.toString());
    }
}
