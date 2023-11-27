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
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.DisplayName;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.Callable;

public abstract class DefaultNativeComponent {
    private final ConfigurableFileCollection source;

    public DefaultNativeComponent(ObjectFactory objectFactory) {
        // TODO - introduce a new 'var' data structure that allows these conventions to be configured explicitly
        source = objectFactory.fileCollection();
    }

    public abstract DisplayName getDisplayName();

    public String toString() {
        return getDisplayName().getDisplayName();
    }

    public ConfigurableFileCollection getSource() {
        return source;
    }

    public void source(Action<? super ConfigurableFileCollection> action) {
        action.execute(source);
    }

    @Inject
    protected ProjectLayout getProjectLayout() {
        throw new UnsupportedOperationException();
    }

    // TODO - this belongs with the 'var' data structure
    protected FileCollection createSourceView(final String defaultLocation, List<String> sourceExtensions) {
        final PatternSet patternSet = new PatternSet();
        for (String sourceExtension : sourceExtensions) {
            patternSet.include("**/*." + sourceExtension);
        }
        return getProjectLayout().files(new Callable<Object>() {
            @Override
            public Object call() {
                FileTree tree;
                if (source.getFrom().isEmpty()) {
                    tree = getProjectLayout().getProjectDirectory().dir(defaultLocation).getAsFileTree();
                } else {
                    tree = source.getAsFileTree();
                }
                return tree.matching(patternSet);
            }
        });
    }
}
