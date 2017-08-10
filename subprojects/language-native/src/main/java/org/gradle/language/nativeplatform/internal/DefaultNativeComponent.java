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

package org.gradle.language.nativeplatform.internal;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.util.PatternSet;

import java.util.List;
import java.util.concurrent.Callable;

public class DefaultNativeComponent {
    private final ConfigurableFileCollection source;
    private final FileOperations fileOperations;

    public DefaultNativeComponent(final FileOperations fileOperations) {
        // TODO - introduce a new 'var' data structure that allows these conventions to be configured explicitly
        this.fileOperations = fileOperations;

        source = fileOperations.files();
    }

    public ConfigurableFileCollection getSource() {
        return source;
    }

    public void source(Action<? super ConfigurableFileCollection> action) {
        action.execute(source);
    }

    protected FileCollection createSourceView(final String defaultLocation, List<String> sourceExtensions) {
        final PatternSet patternSet = new PatternSet();
        for (String sourceExtension : sourceExtensions) {
            patternSet.include("**/*." + sourceExtension);
        }
        return fileOperations.files(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                FileTree tree;
                if (source.isEmpty()) {
                    tree = fileOperations.fileTree(defaultLocation);
                } else {
                    tree = source.getAsFileTree();
                }
                return tree.matching(patternSet);
            }
        });
    }
}
