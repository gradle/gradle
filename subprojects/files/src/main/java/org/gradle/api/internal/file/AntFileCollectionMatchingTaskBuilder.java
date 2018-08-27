/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.tasks.AntBuilderAware;

import java.util.Collections;

public class AntFileCollectionMatchingTaskBuilder implements AntBuilderAware {

    private final Iterable<DirectoryFileTree> fileTrees;

    public AntFileCollectionMatchingTaskBuilder(Iterable<DirectoryFileTree> fileTrees) {
        this.fileTrees = fileTrees;
    }

    public Object addToAntBuilder(final Object node, final String childNodeName) {
        final DynamicObject dynamicObject = new BeanDynamicObject(node);

        final Iterable<DirectoryFileTree> existing = Lists.newLinkedList(
                FluentIterable
                        .from(fileTrees)
                        .filter(new Predicate<DirectoryFileTree>() {
                            @Override
                            public boolean apply(DirectoryFileTree input) {
                                return input.getDir().exists();
                            }
                        })
        );

        for (DirectoryFileTree fileTree : existing) {
            dynamicObject.invokeMethod(childNodeName, Collections.singletonMap("location", fileTree.getDir()));
        }

        dynamicObject.invokeMethod("or", new Closure<Void>(this) {
            public Object doCall(Object ignore) {
                for (final DirectoryFileTree fileTree : existing) {
                    dynamicObject.invokeMethod("and", new Closure<Void>(this) {
                        public Object doCall(Object ignore) {
                            dynamicObject.invokeMethod("gradleBaseDirSelector", Collections.singletonMap("baseDir", fileTree.getDir()));
                            fileTree.getPatterns().addToAntBuilder(node, null);
                            return null;
                        }
                    });
                }
                return null;
            }
        });

        return node;
    }

}
