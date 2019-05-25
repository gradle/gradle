/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.nativeintegration.filesystem;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark                                                Mode  Cnt     Score     Error  Units
 * FileDeduplicationBenchmark.fileSet_immutable            thrpt   10   961.357 ± 144.687  ops/s
 * FileDeduplicationBenchmark.fileSet_immutable_copyOf     thrpt   10  1072.822 ± 161.666  ops/s
 * FileDeduplicationBenchmark.fileSet_linkedHashset        thrpt   10  1624.303 ± 218.461  ops/s
 * FileDeduplicationBenchmark.fileSet_linkedHashset_sized  thrpt   10  2272.650 ± 257.375  ops/s
 **/
@Fork(2)
@State(Scope.Benchmark)
@Warmup(iterations = 0, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
public class FileDeduplicationBenchmark {
    final ArrayList<File> values = new ArrayList<File>();

    @Setup
    public void prepare() {
        Collection<File> files = FileUtils.listFiles(
            new File("/Users/paplorinc/gradle"), // TODO
            new IOFileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.getName().endsWith(".java");
                }

                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".java");
                }
            },
            TrueFileFilter.INSTANCE
        );
        values.addAll(files);
        values.addAll(files); // duplicates
        values.trimToSize();
        System.out.println("values" + values.size());
    }

    @Benchmark
    public Object fileSet_linkedHashset() {
        Set<File> set = Sets.newLinkedHashSet();
        for (File file : values) {
            //noinspection UseBulkOperation
            set.add(file);
        }
        assert set.size() == values.size() / 2;
        return Collections.unmodifiableSet(set);
    }

    @Benchmark
    public Object fileSet_linkedHashset_sized() {
        Set<File> files = unmodifiableSet(Sets.newLinkedHashSet(values));
        assert files.size() == values.size() / 2;
        return files;
    }

    @Benchmark
    public Object fileSet_immutable() {
        ImmutableSet.Builder<File> builder = ImmutableSet.builder();
        for (File file : values) {
            builder.add(file);
        }
        ImmutableSet<File> build = builder.build();
        assert build.size() == values.size() / 2;
        return build;
    }

    @Benchmark
    public Object fileSet_immutable_copyOf() {
        Set<File> files = ImmutableSet.copyOf(values);
        assert files.size() == values.size() / 2;
        return files;
    }
}
