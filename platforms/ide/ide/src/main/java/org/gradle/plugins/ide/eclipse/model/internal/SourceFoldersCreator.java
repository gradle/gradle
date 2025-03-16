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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.metaobject.DynamicObjectUtil;
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;

public class SourceFoldersCreator {

    public List<SourceFolder> createSourceFolders(final EclipseClasspath classpath) {

        List<SourceFolder> sourceFolders = configureProjectRelativeFolders(classpath.getSourceSets(),
            classpath.getTestSourceSets().getOrElse(emptySet()),
            input-> classpath.getProject().relativePath(input),
            classpath.getDefaultOutputDir(),
            classpath.getBaseSourceOutputDir().map(dir -> classpath.getProject().relativePath(dir)).getOrElse("bin"));

        return collectRegularAndExternalSourceFolders(sourceFolders,
            (sourceFoldersLeft, sourceFoldersRight) -> ImmutableList.<SourceFolder>builder()
                .addAll(sourceFoldersLeft)
                .addAll(sourceFoldersRight)
                .build());
    }

    /**
     * Returns the list of external source folders defining only the name and path attributes.
     *
     * @return source folders that live outside of the project
     */
    public List<SourceFolder> getBasicExternalSourceFolders(Iterable<SourceSet> sourceSets, Function<File, String> provideRelativePath, File defaultOutputDir) {
        List<SourceFolder> basicSourceFolders = basicProjectRelativeFolders(sourceSets, provideRelativePath);
        return collectRegularAndExternalSourceFolders(basicSourceFolders, (sourceFoldersLeft, sourceFoldersRight) -> ImmutableList.copyOf(sourceFoldersRight));
    }

    private <T> T collectRegularAndExternalSourceFolders(List<SourceFolder> sourceFolder, BiFunction<Collection<SourceFolder>, Collection<SourceFolder>, T> collector) {
        Pair<Collection<SourceFolder>, Collection<SourceFolder>> partitionedFolders = CollectionUtils.partition(sourceFolder,  sf-> sf.getPath().contains(".."));

        Collection<SourceFolder> externalSourceFolders = partitionedFolders.getLeft();
        Collection<SourceFolder> regularSourceFolders = partitionedFolders.getRight();

        List<String> sources = Lists.newArrayList(Collections2.transform(regularSourceFolders, SourceFolder::getName));
        Collection<SourceFolder> dedupedExternalSourceFolders = trimAndDedup(externalSourceFolders, sources);

        return collector.apply(regularSourceFolders, dedupedExternalSourceFolders);
    }

    private List<SourceFolder> configureProjectRelativeFolders(Iterable<SourceSet> sourceSets, Collection<SourceSet> testSourceSets,
                                                               Function<File, String> provideRelativePath, File defaultOutputDir, String baseSourceOutputDir) {
        String defaultOutputPath = PathUtil.normalizePath(provideRelativePath.apply(defaultOutputDir));
        ImmutableList.Builder<SourceFolder> entries = ImmutableList.builder();
        List<SourceSet> sortedSourceSets = sortSourceSetsAsPerUsualConvention(sourceSets);
        Map<SourceSet, String> sourceSetOutputPaths = collectSourceSetOutputPaths(sortedSourceSets, defaultOutputPath, baseSourceOutputDir);
        Multimap<SourceSet, SourceSet> sourceSetUsages = getSourceSetUsages(sortedSourceSets);
        for (SourceSet sourceSet : sortedSourceSets) {
            List<DirectoryTree> sortedSourceDirs = sortSourceDirsAsPerUsualConvention(sourceSet.getAllSource().getSrcDirTrees());
            for (DirectoryTree tree : sortedSourceDirs) {
                File dir = tree.getDir();
                if (dir.isDirectory()) {
                    SourceFolder folder = createSourceFolder(testSourceSets, provideRelativePath, sourceSetOutputPaths, sourceSetUsages, sourceSet, tree, dir);
                    entries.add(folder);
                }
            }
        }
        return entries.build();
    }

    private SourceFolder createSourceFolder(Collection<SourceSet> testSourceSets, Function<File, String> provideRelativePath, Map<SourceSet, String> sourceSetOutputPaths, Multimap<SourceSet, SourceSet> sourceSetUsages, SourceSet sourceSet, DirectoryTree tree, File dir) {
        String relativePath = provideRelativePath.apply(dir);
        SourceFolder folder = new SourceFolder(relativePath, null);
        folder.setDir(dir);
        folder.setName(dir.getName());
        folder.setIncludes(getIncludesForTree(sourceSet, tree));
        folder.setExcludes(getExcludesForTree(sourceSet, tree));
        folder.setOutput(sourceSetOutputPaths.get(sourceSet));
        addScopeAttributes(folder, sourceSet, sourceSetUsages);
        addSourceSetAttributeIfNeeded(sourceSet, folder, testSourceSets);
        return folder;
    }

    private List<SourceFolder> basicProjectRelativeFolders(Iterable<SourceSet> sourceSets, Function<File, String> provideRelativePath) {
        ImmutableList.Builder<SourceFolder> entries = ImmutableList.builder();
        List<SourceSet> sortedSourceSets = sortSourceSetsAsPerUsualConvention(sourceSets);
        for (SourceSet sourceSet : sortedSourceSets) {
            List<DirectoryTree> sortedSourceDirs = sortSourceDirsAsPerUsualConvention(sourceSet.getAllSource().getSrcDirTrees());
            for (DirectoryTree tree : sortedSourceDirs) {
                File dir = tree.getDir();
                if (dir.isDirectory()) {
                    entries.add(createSourceFolder(provideRelativePath, dir));
                }
            }
        }
        return entries.build();
    }

    private static SourceFolder createSourceFolder(Function<File, String> provideRelativePath, File dir) {
        SourceFolder folder = new SourceFolder(provideRelativePath.apply(dir), null);
        folder.setDir(dir);
        folder.setName(dir.getName());
        return folder;
    }

    private List<SourceFolder> trimAndDedup(Collection<SourceFolder> externalSourceFolders, List<String> givenSources) {
        // externals are mapped to linked resources so we just need a name of the resource, without full path
        // non-unique folder names are naively deduped by adding parent filename as a prefix till unique
        // since this seems like a rare edge case this simple approach should be enough
        List<SourceFolder> trimmedSourceFolders = new ArrayList<>();
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

        private static final Joiner COMMA_JOINER = Joiner.on(',');
    private void addScopeAttributes(SourceFolder folder, SourceSet sourceSet, Multimap<SourceSet, SourceSet> sourceSetUsages) {
        folder.getEntryAttributes().put(EclipsePluginConstants.GRADLE_SCOPE_ATTRIBUTE_NAME, sanitizeNameForAttribute(sourceSet));
        folder.getEntryAttributes().put(EclipsePluginConstants.GRADLE_USED_BY_SCOPE_ATTRIBUTE_NAME, COMMA_JOINER.join(getUsingSourceSetNames(sourceSet, sourceSetUsages)));
    }

    private List<String> getUsingSourceSetNames(SourceSet sourceSet, Multimap<SourceSet, SourceSet> sourceSetUsages) {
        return sourceSetUsages.get(sourceSet).stream()
            .map(this::sanitizeNameForAttribute)
            .collect(toList());
    }

    private String sanitizeNameForAttribute(SourceSet sourceSet) {
        return sourceSet.getName().replaceAll(",", "");
    }

    private Multimap<SourceSet, SourceSet> getSourceSetUsages(Iterable<SourceSet> sourceSets) {
        Multimap<SourceSet, SourceSet> usages = LinkedHashMultimap.create();
        for (SourceSet sourceSet : sourceSets) {
            for (SourceSet otherSourceSet : sourceSets) {
                if (containsOutputOf(sourceSet, otherSourceSet)) {
                    usages.put(otherSourceSet, sourceSet);
                }
            }
        }
        return usages;
    }

    private boolean containsOutputOf(SourceSet sourceSet, SourceSet otherSourceSet) {
        try {
            return containsAll(sourceSet.getRuntimeClasspath(), otherSourceSet.getOutput());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsAll(FileCollection first, FileCollection second) {
        for (File file : second) {
            if (!first.contains(file)) {
                return false;
            }
        }
        return true;
    }

    private void addSourceSetAttributeIfNeeded(SourceSet sourceSet, SourceFolder folder, Collection<SourceSet> testSourceSets) {
        if (testSourceSets.contains(sourceSet)) {
            folder.getEntryAttributes().put(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE);
        }
    }

    private Map<SourceSet, String> collectSourceSetOutputPaths(Iterable<SourceSet> sourceSets, String defaultOutputPath, String baseSourceOutputDir) {
        Set<String> existingPaths = Sets.newHashSet(defaultOutputPath);
        Map<SourceSet, String> result = new HashMap<>();
        for (SourceSet sourceSet : sourceSets) {
            String path = collectSourceSetOutputPath(sourceSet.getName(), existingPaths, "", baseSourceOutputDir);
            existingPaths.add(path);
            result.put(sourceSet, path);
        }

        return result;
    }

    private String collectSourceSetOutputPath(String sourceSetName, Set<String> existingPaths, String suffix, String baseSourceOutputDir) {
        String path = baseSourceOutputDir + "/" + sourceSetName + suffix;
        return existingPaths.contains(path) ? collectSourceSetOutputPath(sourceSetName, existingPaths, suffix + "_", baseSourceOutputDir) : path;
    }

    private List<String> getExcludesForTree(SourceSet sourceSet, DirectoryTree directoryTree) {
        List<Set<String>> excludesByType = getFiltersForTreeGroupedByType(sourceSet, directoryTree, "excludes");
        return CollectionUtils.intersection(excludesByType);
    }

    private List<String> getIncludesForTree(SourceSet sourceSet, DirectoryTree directoryTree) {
        List<Set<String>> includesByType = getFiltersForTreeGroupedByType(sourceSet, directoryTree, "includes");
        if(includesByType.stream().anyMatch(Set::isEmpty)) {
            return emptyList();
        }

        List<String> allIncludes = CollectionUtils.flattenCollections(String.class, includesByType);
        return ImmutableSet.copyOf(allIncludes).asList();
    }

    private List<Set<String>> getFiltersForTreeGroupedByType(SourceSet sourceSet, DirectoryTree directoryTree, String filterOperation) {
        // check for duplicate entries in java and resources
        Set<File> javaSrcDirs = sourceSet.getAllJava().getSrcDirs();
        Set<File> resSrcDirs = sourceSet.getResources().getSrcDirs();
        List<File> srcDirs = CollectionUtils.intersection(ImmutableList.of(javaSrcDirs, resSrcDirs));
        if (!srcDirs.contains(directoryTree.getDir())) {
            return ImmutableList.of(collectFilters(directoryTree.getPatterns(), filterOperation));
        } else {
            Set<String> resourcesFilter = collectFilters(sourceSet.getResources().getSrcDirTrees(), directoryTree.getDir(), filterOperation);
            Set<String> sourceFilter = collectFilters(sourceSet.getAllJava().getSrcDirTrees(), directoryTree.getDir(), filterOperation);
            return ImmutableList.of(resourcesFilter, sourceFilter);
        }
    }

    private Set<String> collectFilters(Set<DirectoryTree> directoryTrees, File targetDir, String filterOperation) {
        for (DirectoryTree directoryTree : directoryTrees) {
            if (directoryTree.getDir().equals(targetDir)) {
                PatternSet patterns = directoryTree.getPatterns();
                return collectFilters(patterns, filterOperation);
            }
        }
        return emptySet();
    }

    private Set<String> collectFilters(PatternSet patterns, String filterOperation) {
        return Cast.<Set<String>>uncheckedCast(DynamicObjectUtil.asDynamicObject(patterns).getProperty(filterOperation));
    }

    private List<SourceSet> sortSourceSetsAsPerUsualConvention(Iterable<SourceSet> sourceSets) {
        return CollectionUtils.sort(sourceSets, Comparator.comparing(SourceFoldersCreator::toComparable));
    }

    private List<DirectoryTree> sortSourceDirsAsPerUsualConvention(Iterable<DirectoryTree> sourceDirs) {
        return CollectionUtils.sort(sourceDirs, Comparator.comparing(SourceFoldersCreator::toComparable));
    }

    private static Integer toComparable(SourceSet sourceSet) {
        String name = sourceSet.getName();
        if (SourceSet.MAIN_SOURCE_SET_NAME.equals(name)) {
            return 0;
        } else if (SourceSet.TEST_SOURCE_SET_NAME.equals(name)) {
            return 1;
        } else {
            return 2;
        }
    }

    private static Integer toComparable(DirectoryTree tree) {
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
