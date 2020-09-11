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

package org.gradle.api.internal.tasks.scala;

import com.google.common.collect.Iterables;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.cache.internal.MapBackedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GFileUtils;
import sbt.internal.inc.Analysis;
import sbt.internal.inc.ExternalLookup;
import sbt.internal.inc.IncrementalCompilerImpl;
import sbt.internal.inc.Locate;
import sbt.internal.inc.LoggedReporter;
import sbt.internal.inc.ScalaInstance;
import sbt.internal.inc.Stamper;
import scala.None$;
import scala.Option;
import scala.Some;
import scala.collection.JavaConverters;
import scala.collection.immutable.HashSet;
import scala.collection.immutable.Set;
import xsbti.T2;
import xsbti.compile.AnalysisContents;
import xsbti.compile.AnalysisStore;
import xsbti.compile.Changes;
import xsbti.compile.ClassFileManager;
import xsbti.compile.ClassFileManagerType;
import xsbti.compile.ClasspathOptionsUtil;
import xsbti.compile.CompileAnalysis;
import xsbti.compile.CompileOptions;
import xsbti.compile.CompileResult;
import xsbti.compile.CompilerCache;
import xsbti.compile.Compilers;
import xsbti.compile.DefinesClass;
import xsbti.compile.ExternalHooks;
import xsbti.compile.FileHash;
import xsbti.compile.IncOptions;
import xsbti.compile.Inputs;
import xsbti.compile.PerClasspathEntryLookup;
import xsbti.compile.PreviousResult;
import xsbti.compile.ScalaCompiler;
import xsbti.compile.Setup;
import xsbti.compile.TransactionalManagerType;
import xsbti.compile.analysis.Stamp;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ZincScalaCompiler implements Compiler<ScalaJavaJointCompileSpec> {
    private static final Logger LOGGER = Logging.getLogger(ZincScalaCompiler.class);

    private final ScalaInstance scalaInstance;
    private final ScalaCompiler scalaCompiler;
    private final AnalysisStoreProvider analysisStoreProvider;
    private final boolean leakCompilerClasspath;

    private final ClearableMapBackedCache<File, DefinesClass> definesClassCache = new ClearableMapBackedCache<>(new ConcurrentHashMap<>());

    @Inject
    public ZincScalaCompiler(ScalaInstance scalaInstance, ScalaCompiler scalaCompiler, AnalysisStoreProvider analysisStoreProvider, boolean leakCompilerClasspath) {
        this.scalaInstance = scalaInstance;
        this.scalaCompiler = scalaCompiler;
        this.analysisStoreProvider = analysisStoreProvider;
        this.leakCompilerClasspath = leakCompilerClasspath;
    }

    public WorkResult execute(final ScalaJavaJointCompileSpec spec) {

        LOGGER.info("Compiling with Zinc Scala compiler.");

        Timer timer = Time.startTimer();

        IncrementalCompilerImpl incremental = new IncrementalCompilerImpl();

        Compilers compilers = incremental.compilers(scalaInstance, ClasspathOptionsUtil.boot(), Option.apply(Jvm.current().getJavaHome()), scalaCompiler);

        List<String> scalacOptions = new ZincScalaCompilerArgumentsGenerator().generate(spec);
        List<String> javacOptions = new JavaCompilerArgumentsBuilder(spec).includeClasspath(false).noEmptySourcePath().build();

        File[] classpath;
        if (leakCompilerClasspath) {
            classpath = Iterables.toArray(Iterables.concat(Arrays.asList(scalaInstance.allJars()), spec.getCompileClasspath()), File.class);
        } else {
            classpath = Iterables.toArray(spec.getCompileClasspath(), File.class);
        }

        CompileOptions compileOptions = CompileOptions.create()
                .withSources(Iterables.toArray(spec.getSourceFiles(), File.class))
                .withClasspath(classpath)
                .withScalacOptions(scalacOptions.toArray(new String[0]))
                .withClassesDirectory(spec.getDestinationDir())
                .withJavacOptions(javacOptions.toArray(new String[0]));

        File analysisFile = spec.getAnalysisFile();
        Optional<AnalysisStore> analysisStore;
        Optional<ClassFileManagerType> classFileManagerType;
        if (spec.getScalaCompileOptions().isForce()) {
            analysisStore = Optional.empty();
            classFileManagerType = IncOptions.defaultClassFileManagerType();
        } else {
            analysisStore = Optional.of(analysisStoreProvider.get(analysisFile));
            classFileManagerType = Optional.of(TransactionalManagerType.of(spec.getClassfileBackupDir(), new SbtLoggerAdapter()));
        }

        PreviousResult previousResult;
        previousResult = analysisStore.flatMap(store -> store.get()
            .map(a -> PreviousResult.of(Optional.of(a.getAnalysis()), Optional.of(a.getMiniSetup()))))
            .orElse(PreviousResult.of(Optional.empty(), Optional.empty()));

        IncOptions incOptions = IncOptions.of()
                .withExternalHooks(new LookupOnlyExternalHooks(new ExternalBinariesLookup()))
                .withRecompileOnMacroDef(Optional.of(false))
                .withClassfileManagerType(classFileManagerType)
                .withTransitiveStep(5);

        Setup setup = incremental.setup(new EntryLookup(spec),
                false,
                analysisFile,
                CompilerCache.fresh(),
                incOptions,
                new LoggedReporter(100, new SbtLoggerAdapter(), p -> p),
                Option.empty(),
                getExtra()
        );

        Inputs inputs = incremental.inputs(compileOptions, compilers, setup, previousResult);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(inputs.toString());
        }
        if (spec.getScalaCompileOptions().isForce()) {
            // TODO This should use Deleter
            GFileUtils.deleteDirectory(spec.getDestinationDir());
            GFileUtils.deleteQuietly(spec.getAnalysisFile());
        }
        LOGGER.info("Prepared Zinc Scala inputs: {}", timer.getElapsed());

        try {
            CompileResult compile = incremental.compile(inputs, new SbtLoggerAdapter());
            if (analysisStore.isPresent()) {
                AnalysisContents contentNext = AnalysisContents.create(compile.analysis(), compile.setup());
                analysisStore.get().set(contentNext);
            }
        } catch (xsbti.CompileFailed e) {
            throw new CompilationFailedException(e);
        }
        LOGGER.info("Completed Scala compilation: {}", timer.getElapsed());
        return WorkResults.didWork(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static T2<String, String>[] getExtra() {
        return new T2[0];
    }

    private class EntryLookup implements PerClasspathEntryLookup {
        private final Map<File, File> analysisMap;

        public EntryLookup(ScalaJavaJointCompileSpec spec) {
            this.analysisMap = new HashMap<>();
            analysisMap.put(spec.getDestinationDir(), spec.getAnalysisFile());
            analysisMap.putAll(spec.getAnalysisMap());
        }

        @Override
        public Optional<CompileAnalysis> analysis(File classpathEntry) {
            return Optional.ofNullable(analysisMap.get(classpathEntry)).flatMap(f -> analysisStoreProvider.get(f).get().map(a -> a.getAnalysis()));
        }

        @Override
        public DefinesClass definesClass(File classpathEntry) {
            Optional<DefinesClass> dc = analysis(classpathEntry).map(a -> a instanceof Analysis ? (Analysis) a : null).map(a -> new AnalysisBakedDefineClass(a));
            return dc.orElseGet(() -> {
                return definesClassCache.get(classpathEntry, new Factory<DefinesClass>() {
                    @Nullable
                    @Override
                    public DefinesClass create() {
                        return Locate.definesClass(classpathEntry);
                    }
                });
            });
        }
    }

    private static class ExternalBinariesLookup implements ExternalLookup {

        @SuppressWarnings("unchecked")
        public <T> Option<T> none() {
            return (Option<T>) None$.MODULE$;
        }

        @Override
        public Option<Changes<File>> changedSources(CompileAnalysis previousAnalysis) {
            return none();
        }


        @Override
        public Option<Set<File>> changedBinaries(CompileAnalysis previousAnalysis) {
            java.util.List<File> result = new java.util.ArrayList<File>();

            for (Map.Entry<File, Stamp> e : previousAnalysis.readStamps().getAllBinaryStamps().entrySet()) {
                if (!e.getKey().exists() || !e.getValue().equals(Stamper.forLastModified().apply(e.getKey()))) {
                    result.add(e.getKey());
                }
            }
            //return new Some<Set<File>>(new HashSet<>());
            //return Option.empty();
            if (result.isEmpty()) {
                return new Some<Set<File>>(new HashSet<>());
            } else {
                return new Some<Set<File>>(JavaConverters.asScalaBuffer(result).<File>toSet());
            }
        }


        @Override
        public Option<Set<File>> removedProducts(CompileAnalysis previousAnalysis) {
            return new Some<Set<File>>(new HashSet<>()); //return none();
        }

        @Override
        public boolean shouldDoIncrementalCompilation(Set<String> changedClasses, CompileAnalysis analysis) {
            return true;
        }


        @Override
        public Optional<FileHash[]> hashClasspath(File[] classpath) {
            return Optional.empty();
        }
    }

    private static class LookupOnlyExternalHooks implements ExternalHooks {
        private final Optional<Lookup> lookup;

        public LookupOnlyExternalHooks(Lookup lookup) {
            this.lookup = Optional.of(lookup);
        }

        @Override
        public Optional<Lookup> getExternalLookup() {
            return lookup;
        }

        @Override
        public Optional<ClassFileManager> getExternalClassFileManager() {
            return Optional.empty();
        }

        @Override
        public ExternalHooks withExternalClassFileManager(ClassFileManager externalClassFileManager) {
            return this;
        }

        @Override
        public ExternalHooks withExternalLookup(Lookup externalLookup) {
            return this;
        }
    }

    private static class AnalysisBakedDefineClass implements DefinesClass {
        private final Analysis analysis;

        public AnalysisBakedDefineClass(Analysis analysis) {
            this.analysis = analysis;
        }

        @Override
        public boolean apply(String className) {
            return analysis.relations().productClassName().reverse(className).nonEmpty();
        }
    }

    private static class ClearableMapBackedCache<K, V> extends MapBackedCache<K, V> {

        private final Map<K, V> map;

        public ClearableMapBackedCache(Map<K, V> map) {
            super(map);
            this.map = map;
        }

        public void clear() {
            map.clear();
        }
    }
}
