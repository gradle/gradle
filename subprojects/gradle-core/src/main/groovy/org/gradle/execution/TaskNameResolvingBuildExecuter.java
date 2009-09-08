/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.execution;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.util.GUtil;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * A {@link BuildExecuter} which selects tasks which match the provided names. For each name, selects all tasks in all
 * projects whose name is the given name.
 */
public class TaskNameResolvingBuildExecuter implements BuildExecuter {
    private final List<String> names;
    private String description;
    private TaskExecuter executer;

    public TaskNameResolvingBuildExecuter(Collection<String> names) {
        this.names = new ArrayList<String>(names);
    }

    public void select(GradleInternal gradle) {
        Map<String, Collection<Task>> selectedTasks = doSelect(gradle, names);

        this.executer = gradle.getTaskGraph();
        for (Collection<Task> tasksForName : selectedTasks.values()) {
            executer.addTasks(tasksForName);
        }
        if (selectedTasks.size() == 1) {
            description = String.format("primary task %s", GUtil.toString(selectedTasks.keySet()));
        } else {
            description = String.format("primary tasks %s", GUtil.toString(selectedTasks.keySet()));
        }
    }

    static List<Collection<Task>> select(GradleInternal gradle, Iterable<String> names) {
        return new ArrayList<Collection<Task>>(doSelect(gradle, names).values());
    }

    private static Map<String, Collection<Task>> doSelect(GradleInternal gradle, Iterable<String> paths) {
        Project defaultProject = gradle.getDefaultProject();

        Map<String, Collection<Task>> allProjectsTasksByName = null;

        Map<String, Collection<Task>> matches = new LinkedHashMap<String, Collection<Task>>();
        for (String path : paths) {
            Map<String, Collection<Task>> tasksByName;
            String baseName;
            String prefix;

            if (path.contains(Project.PATH_SEPARATOR)) {
                prefix = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
                prefix = prefix.length() == 0 ? Project.PATH_SEPARATOR : prefix;
                Project project = defaultProject.findProject(prefix);
                if (project == null) {
                    throw new TaskSelectionException(String.format("Project '%s' not found in %s.", prefix,
                            defaultProject));
                }
                baseName = StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR);
                Task match = project.getTasks().findByName(baseName);
                if (match != null) {
                    matches.put(path, Collections.singleton(match));
                    continue;
                }

                tasksByName = new HashMap<String, Collection<Task>>();
                for (Task task : project.getTasks().getAll()) {
                    tasksByName.put(task.getName(), Collections.singleton(task));
                }
                prefix = prefix + Project.PATH_SEPARATOR;

            }
            else {
                Set<Task> tasks = defaultProject.getTasksByName(path, true);
                if (!tasks.isEmpty()) {
                    matches.put(path, tasks);
                    continue;
                }
                if (allProjectsTasksByName == null) {
                    allProjectsTasksByName = buildTaskMap(defaultProject);
                }
                tasksByName = allProjectsTasksByName;
                baseName = path;
                prefix = "";
            }

            Pattern pattern = getPatternForName(baseName);
            Set<String> patternCandidates = new TreeSet<String>();
            Set<String> typoCandidates = new TreeSet<String>();
            for (String candidate : tasksByName.keySet()) {
                if (pattern.matcher(candidate).matches()) {
                    patternCandidates.add(candidate);
                }
                if (StringUtils.getLevenshteinDistance(baseName.toUpperCase(), candidate.toUpperCase()) <= Math.min(3,
                        baseName.length() / 2)) {
                    typoCandidates.add(candidate);
                }
            }
            if (patternCandidates.size() == 1) {
                String actualName = patternCandidates.iterator().next();
                matches.put(prefix + actualName, tasksByName.get(actualName));
                continue;
            }

            if (!patternCandidates.isEmpty()) {
                throw new TaskSelectionException(String.format("Task '%s' is ambiguous in %s. Candidates are: %s.",
                        baseName, defaultProject, GUtil.toString(patternCandidates)));
            }
            if (!typoCandidates.isEmpty()) {
                throw new TaskSelectionException(String.format("Task '%s' not found in %s. Some candidates are: %s.",
                        baseName, defaultProject, GUtil.toString(typoCandidates)));
            }
            throw new TaskSelectionException(String.format("Task '%s' not found in %s.", baseName, defaultProject));
        }

        return matches;
    }

    private static Pattern getPatternForName(String name) {
        Pattern boundaryPattern = Pattern.compile("(^\\p{javaLowerCase}+)|(\\p{javaUpperCase}\\p{javaLowerCase}*)");
        Matcher matcher = boundaryPattern.matcher(name);
        int pos = 0;
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            builder.append(Pattern.quote(name.substring(pos, matcher.start())));
            builder.append(matcher.group());
            builder.append("\\p{javaLowerCase}*");
            pos = matcher.end();
        }
        builder.append(Pattern.quote(name.substring(pos, name.length())));
        builder.append(".*");
        return Pattern.compile(builder.toString());
    }

    private static Map<String, Collection<Task>> buildTaskMap(Project defaultProject) {
        Map<String, Collection<Task>> tasksByName = new HashMap<String, Collection<Task>>();
        for (Project project : defaultProject.getAllprojects()) {
            for (Task task : project.getTasks().getAll()) {
                Collection<Task> tasksForName = tasksByName.get(task.getName());
                if (tasksForName == null) {
                    tasksForName = new HashSet<Task>();
                    tasksByName.put(task.getName(), tasksForName);
                }
                tasksForName.add(task);
            }
        }
        return tasksByName;
    }

    public String getDisplayName() {
        return description;
    }

    public void execute() {
        executer.execute();
    }
}
