package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoExtractor;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoSerializer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.util.Clock;

import java.io.File;

public class IncrementalCompilationSupport {

    private static final Logger LOG = Logging.getLogger(IncrementalCompilationSupport.class);
    private final ClassDependencyInfoExtractor extractor = new ClassDependencyInfoExtractor();

    public void compilationComplete(CompileOptions options, File compilationDestDir, ClassDependencyInfoSerializer infoSerializer) {
        if (options.isIncremental()) {
            Clock clock = new Clock();
            ClassDependencyInfo info = extractor.extractInfo(compilationDestDir);
            infoSerializer.writeInfo(info);
            LOG.lifecycle("Performed class dependency analysis in {}, wrote results into {}", clock.getTime(), infoSerializer);
        }
    }
}
