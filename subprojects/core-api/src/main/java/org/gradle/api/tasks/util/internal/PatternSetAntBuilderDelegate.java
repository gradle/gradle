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

package org.gradle.api.tasks.util.internal;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.gradle.api.Action;
import org.gradle.api.tasks.AntBuilderAware;
import org.gradle.util.AntUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Externalised from PatternSet to isolate the Groovy usage.
 */
public class PatternSetAntBuilderDelegate implements AntBuilderAware {

    private final Set<String> includes;
    private final Set<String> excludes;
    private final boolean caseSensitive;

    public PatternSetAntBuilderDelegate(Set<String> includes, Set<String> excludes, boolean caseSensitive) {
        this.includes = includes;
        this.excludes = excludes;
        this.caseSensitive = caseSensitive;
    }

    private static Object logical(Object node, String op, final Action<Object> withNode) {
        GroovyObject groovyObject = (GroovyObject) node;
        groovyObject.invokeMethod(op, new Closure(null, null) {
            void doCall() {
                withNode.execute(getDelegate());
            }
        });
        return node;
    }

    public static Object and(Object node, Action<Object> withNode) {
        return logical(node, "and", withNode);
    }

    private static Object or(Object node, Action<Object> withNode) {
        return logical(node, "or", withNode);
    }

    private static Object not(Object node, Action<Object> withNode) {
        return logical(node, "not", withNode);
    }

    private static Object addFilenames(Object node, Iterable<String> filenames, boolean caseSensitive) {
        GroovyObject groovyObject = (GroovyObject) node;
        Map<String, Object> props = new HashMap<String, Object>(2);
        props.put("casesensitive", caseSensitive);
        for (String filename : filenames) {
            props.put("name", AntUtil.maskFilename(filename));
            groovyObject.invokeMethod("filename", props);
        }
        return node;
    }

    public Object addToAntBuilder(Object node, String childNodeName) {
        return and(node, new Action<Object>() {
            public void execute(Object node) {
                if (!includes.isEmpty()) {
                    or(node, new Action<Object>() {
                        public void execute(Object node) {
                            addFilenames(node, includes, caseSensitive);
                        }
                    });
                }

                if (!excludes.isEmpty()) {
                    not(node, new Action<Object>() {
                        public void execute(Object node) {
                            or(node, new Action<Object>() {
                                public void execute(Object node) {
                                    addFilenames(node, excludes, caseSensitive);
                                }
                            });
                        }
                    });
                }
            }
        });
    }

}
