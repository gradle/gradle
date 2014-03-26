package org.gradle.api.internal.file.copy;


import org.gradle.api.Action;
import org.gradle.api.file.*;
import org.gradle.api.specs.Spec;

import java.util.Collection;
import java.util.List;

public interface CopySpecResolver {

    boolean isCaseSensitive();
    Integer getFileMode();
    Integer getDirMode();
    boolean getIncludeEmptyDirs();

    RelativePath getDestPath();

    FileTree getSource();

    FileTree getAllSource();

    Collection<? extends Action<? super FileCopyDetails>> getAllCopyActions();

    public List<String> getAllIncludes();

    public List<String> getAllExcludes();

    public List<Spec<FileTreeElement>> getAllIncludeSpecs();

    public List<Spec<FileTreeElement>> getAllExcludeSpecs();

    DuplicatesStrategy getDuplicatesStrategy();

    void walk(Action<? super CopySpecResolver> action);


}
