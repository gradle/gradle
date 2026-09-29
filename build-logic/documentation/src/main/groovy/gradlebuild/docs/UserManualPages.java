/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.docs;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The file names of the pages of the multi-page user manual, as they appear after {@code stageUserguideSource}
 * flattens every {@code .adoc} file of the user manual into one directory.
 * <p>
 * The Asciidoctor Gradle plugin only honours include patterns when selecting the files to convert,
 * so the pages are listed explicitly instead of excluding the files that are only included by other pages.
 * As a value source, the list is recomputed on every build, so new or removed pages invalidate the configuration cache.
 */
public abstract class UserManualPages implements ValueSource<List<String>, UserManualPages.Parameters> {

    public interface Parameters extends ValueSourceParameters {
        DirectoryProperty getUserManualRoot();

        /**
         * Regular expressions for file names that are not rendered as pages of their own.
         */
        ListProperty<String> getExcludedFileNames();
    }

    @Override
    public List<String> obtain() {
        List<Pattern> excluded = getParameters().getExcludedFileNames().get().stream().map(Pattern::compile).collect(Collectors.toList());
        Path root = getParameters().getUserManualRoot().get().getAsFile().toPath();
        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".adoc"))
                .filter(name -> excluded.stream().noneMatch(pattern -> pattern.matcher(name).matches()))
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
