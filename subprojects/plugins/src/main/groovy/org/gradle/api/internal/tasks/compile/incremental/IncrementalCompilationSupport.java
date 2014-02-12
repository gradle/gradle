package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoExtractor;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoSerializer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.util.Clock;

public class IncrementalCompilationSupport {

    private static final Logger LOG = Logging.getLogger(IncrementalCompilationSupport.class);

    public void compilationComplete(CompileOptions options,
                                    ClassDependencyInfoExtractor extractor, ClassDependencyInfoSerializer serializer) {
        if (options.isIncremental()) {
            Clock clock = new Clock();
            ClassDependencyInfo info = extractor.extractInfo("");
            serializer.writeInfo(info);
            LOG.lifecycle("Performed class dependency analysis in {}, wrote results into {}", clock.getTime(), serializer);
        }
    }
}
