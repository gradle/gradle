/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.internal.DynamicObjectUtil;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;


public class SourceFoldersCreator {

    public List<ClasspathEntry> createSourceFolders(final EclipseClasspath classpath) {
        List<ClasspathEntry> entries = Lists.newArrayList();
        Function<File, String> provideRelativePath = new Function<File, String>() {
            @Override
            public String apply(File input) {
                return classpath.getProject().relativePath(input);
            }
        };
        List<SourceFolder> regulars = getRegularSourceFolders(classpath.getSourceSets(), provideRelativePath);
        List<SourceFolder> trimmedExternals = getExternalSourceFolders(classpath.getSourceSets(), provideRelativePath);
        entries.addAll(regulars);
        entries.addAll(trimmedExternals);
        return entries;
    }

    /**
     * paths that navigate higher than project dir are not allowed in eclipse .classpath
     * regardless if they are absolute or relative
     *
     * @return source folders that live inside the project
     */
    public List<SourceFolder> getRegularSourceFolders(Iterable<SourceSet> sourceSets, Function<File, String> provideRelativePath) {
        List<SourceFolder> sourceFolders = projectRelativeFolders(sourceSets, provideRelativePath);
        return CollectionUtils.filter(sourceFolders, new Spec<SourceFolder>() {
            @Override
            public boolean isSatisfiedBy(SourceFolder element) {
                return !element.getPath().contains("..");
            }
        });
    }

    /**
     * see {@link #getRegularSourceFolders}
     *
     * @return source folders that live outside of the project
     */
    public List<SourceFolder> getExternalSourceFolders(Iterable<SourceSet> sourceSets, Function<File, String> provideRelativePath) {
        List<SourceFolder> sourceFolders = projectRelativeFolders(sourceSets, provideRelativePath);
        List<SourceFolder> externalSourceFolders = CollectionUtils.filter(sourceFolders, new Spec<SourceFolder>() {
            @Override
            public boolean isSatisfiedBy(SourceFolder element) {
                return element.getPath().contains("..");
            }
        });
        List<SourceFolder> regularSourceFolders = getRegularSourceFolders(sourceSets, provideRelativePath);
        List<String> sources = Lists.newArrayList(Lists.transform(regularSourceFolders, new Function<SourceFolder, String>() {
            @Override
            public String apply(SourceFolder sourceFolder) {
                return sourceFolder.getName();
            }
        }));
        return trimAndDedup(externalSourceFolders, sources);
    }

    private List<SourceFolder> trimAndDedup(List<SourceFolder> externalSourceFolders, List<String> givenSources) {
        // externals are mapped to linked resources so we just need a name of the resource, without full path
        // non unique folder names are naively deduped by adding parent filename as a prefix till unique
        // since this seems like a rare edge case this simple approach should be enough
        List<SourceFolder> trimmedSourceFolders = Lists.newArrayList();
        for (SourceFolder folder : externalSourceFolders) {
            folder.trim();
            File parentFile = folder.getDir().getParentFile();
            while (givenSources.contains(folder.getName()) && parentFile != null) {
                folder.trim(parentFile.getName());
                parentFile = parentFile.getParentFile();
            }
            givenSources.add(folder.getName());
            trimmedSourceFolders.add(folder);
        }
        return trimmedSourceFolders;
    }

    private List<SourceFolder> projectRelativeFolders(Iterable<SourceSet> sourceSets, Function<File, String> provideRelativePath) {
        ArrayList<SourceFolder> entries = Lists.newArrayList();
        List<SourceSet> sortedSourceSets = sortSourceSetsAsPerUsualConvention(sourceSets);
        for (SourceSet sourceSet : sortedSourceSets) {
            List<DirectoryTree> sortedSourceDirs = sortSourceDirsAsPerUsualConvention(sourceSet.getAllSource().getSrcDirTrees());
            for (DirectoryTree tree : sortedSourceDirs) {
                File dir = tree.getDir();
                if (dir.isDirectory()) {
                    String relativePath = provideRelativePath.apply(dir);
                    SourceFolder folder = new SourceFolder(relativePath, null);
                    folder.setDir(dir);
                    folder.setName(dir.getName());
                    folder.setIncludes(getIncludesForTree(sourceSet, tree));
                    folder.setExcludes(getExcludesForTree(sourceSet, tree));
                    entries.add(folder);
                }
            }
        }
        return entries;
    }

    private List<String> getExcludesForTree(SourceSet sourceSet, DirectoryTree directoryTree) {
        List<Set<String>> excludesByType = getFiltersForTreeGroupedByType(sourceSet, directoryTree, "excludes");
        return CollectionUtils.intersection(excludesByType);
    }

    private List<String> getIncludesForTree(SourceSet sourceSet, DirectoryTree directoryTree) {
        List<Set<String>> includesByType = getFiltersForTreeGroupedByType(sourceSet, directoryTree, "includes");
        for (Set<String> it : includesByType) {
            if (it.isEmpty()) {
                return Collections.emptyList();
            }
        }

        List<String> allIncludes = CollectionUtils.flattenCollections(String.class, includesByType);
        return CollectionUtils.dedup(allIncludes, Equivalence.equals());
    }

    private List<Set<String>> getFiltersForTreeGroupedByType(SourceSet sourceSet, DirectoryTree directoryTree, String filterOperation) {
        // check for duplicate entries in java and resources
        Set<File> javaSrcDirs = sourceSet.getAllJava().getSrcDirs();
        Set<File> resSrcDirs = sourceSet.getResources().getSrcDirs();
        List<File> srcDirs = CollectionUtils.intersection(Lists.newArrayList(javaSrcDirs, resSrcDirs));
        if (!srcDirs.contains(directoryTree.getDir())) {
            return Lists.<Set<String>>newArrayList(collectFilters(directoryTree.getPatterns(), filterOperation));
        } else {
            Set<String> resourcesFilter = collectFilters(sourceSet.getResources().getSrcDirTrees(), directoryTree.getDir(), filterOperation);
            Set<String> sourceFilter = collectFilters(sourceSet.getAllJava().getSrcDirTrees(), directoryTree.getDir(), filterOperation);
            return Lists.<Set<String>>newArrayList(resourcesFilter, sourceFilter);
        }
    }

    private Set<String> collectFilters(Set<DirectoryTree> directoryTrees, File targetDir, String filterOperation) {
        for (DirectoryTree directoryTree : directoryTrees) {
            if (directoryTree.getDir().equals(targetDir)) {
                PatternSet patterns = directoryTree.getPatterns();
                return collectFilters(patterns, filterOperation);
            }
        }
        return Collections.emptySet();
    }

    private Set<String> collectFilters(PatternSet patterns, String filterOperation) {
        return Cast.<Set<String>>uncheckedCast(DynamicObjectUtil.asDynamicObject(patterns).getProperty(filterOperation));
    }

    private List<SourceSet> sortSourceSetsAsPerUsualConvention(Iterable<SourceSet> sourceSets) {
        return CollectionUtils.sort(sourceSets, new Comparator<SourceSet>() {
            @Override
            public int compare(SourceSet left, SourceSet right) {
                return toComparable(left).compareTo(toComparable(right));
            }
        });
    }

    private List<DirectoryTree> sortSourceDirsAsPerUsualConvention(Iterable<DirectoryTree> sourceDirs) {
        return CollectionUtils.sort(sourceDirs, new Comparator<DirectoryTree>() {
            @Override
            public int compare(DirectoryTree left, DirectoryTree right) {
                return toComparable(left).compareTo(toComparable(right));
            }
        });
    }

    private static Comparable toComparable(SourceSet sourceSet) {
        String name = sourceSet.getName();
        if (SourceSet.MAIN_SOURCE_SET_NAME.equals(name)) {
            return 0;
        } else if (SourceSet.TEST_SOURCE_SET_NAME.equals(name)) {
            return 1;
        } else {
            return 2;
        }
    }

    private static Comparable toComparable(DirectoryTree tree) {
        String path = tree.getDir().getPath();
        if (path.endsWith("java")) {
            return 0;
        } else if (path.endsWith("resources")) {
            return 2;
        } else {
            return 1;
        }
    }
}
