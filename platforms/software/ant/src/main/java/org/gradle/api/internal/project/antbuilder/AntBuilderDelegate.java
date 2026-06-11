/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.project.antbuilder;

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.IntersectionPatternSet;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.DynamicObjectUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AntBuilderDelegate {

    private final DynamicObject builder;
    private final ClassLoader antlibClassLoader;

    private Object current;

    public AntBuilderDelegate(Object builder, ClassLoader antlibClassLoader) {
        this.builder = DynamicObjectUtil.asDynamicObject(builder);
        this.antlibClassLoader = antlibClassLoader;
    }

    public void addFiles(String childNodeName, Iterable<File> params) {
        createNode(childNodeName, Collections.emptyMap(), () -> {
            for (File file : params) {
                String filename = maskFilename(file.getAbsolutePath());
                createNode("file", Collections.singletonMap("file", filename));
            }
        });
    }

    public void addDirectoryTrees(String childNodeName, Collection<DirectoryTree> directoryTrees) {
        for (DirectoryTree tree : directoryTrees) {
            if (!tree.getDir().exists()) {
                continue;
            }

            String directory = maskFilename(tree.getDir().getAbsolutePath());
            createNode(childNodeName, Collections.singletonMap("dir", directory), () -> {
                addPatternSet(tree.getPatterns());
            });
        }
    }

    private void addPatternSet(PatternSet patterns) {
        if (!patterns.getIncludeSpecsView().isEmpty() || !patterns.getExcludeSpecsView().isEmpty()) {
            throw new UnsupportedOperationException("Cannot add include/exclude specs to Ant node. Only include/exclude patterns are currently supported.");
        }

        addPatternToAntBuilder(patterns);
    }

    private void addPatternToAntBuilder(PatternSet patterns) {
        if (patterns instanceof IntersectionPatternSet) {
            createNode("and", Collections.emptyMap(), () -> {
                addIncludesAndExcludes(patterns);
                addPatternToAntBuilder(((IntersectionPatternSet) patterns).getOther());
            });
        } else {
            addIncludesAndExcludes(patterns);
        }
    }

    private void addIncludesAndExcludes(PatternSet patterns) {
        createNode("and", Collections.emptyMap(), () -> {
            boolean caseSensitive = patterns.isCaseSensitive();
            Set<String> includes = patterns.getIncludesView();
            if (!includes.isEmpty()) {
                createNode("or", Collections.emptyMap(), () ->
                    addFilenames(includes, caseSensitive)
                );
            }

            Set<String> excludes = patterns.getExcludesView();
            if (!excludes.isEmpty()) {
                createNode("not", Collections.emptyMap(), () -> {
                    createNode("or", Collections.emptyMap(), () ->
                        addFilenames(excludes, caseSensitive)
                    );
                });
            }
        });
    }

    private void addFilenames(Iterable<String> filenames, boolean caseSensitive) {
        Map<String, Object> props = new HashMap<>(2);
        props.put("casesensitive", caseSensitive);
        for (String filename : filenames) {
            props.put("name", maskFilename(filename));
            createNode("filename", props);
        }
    }

    public void taskdef(String name, String classname) {
        try {
            getProject().invokeMethod("addTaskDefinition", name, antlibClassLoader.loadClass(classname));
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void createNode(String methodName, String content) {
        Object node = builder.invokeMethod("createNode", methodName, content);
        nodeCompleted(current, node);
    }

    public void createNode(String methodName, Map<String, Object> parameters) {
        Object node = builder.invokeMethod("createNode", methodName, parameters);
        nodeCompleted(current, node);
    }

    public void createNode(String methodName, Map<String, Object> parameters, Runnable closure) {
        Object node = builder.invokeMethod("createNode", methodName, parameters);

        if (closure != null) {
            // push new node on stack
            Object oldCurrent = current;
            this.current = node;
            closure.run();
            this.current = oldCurrent;
        }

        nodeCompleted(current, node);
    }

    /**
     * A hook to allow nodes to be processed once they have had all of their
     * children applied.
     *
     * @param node   the current node being processed
     * @param parent the parent of the node being processed
     */
    private void nodeCompleted(Object parent, Object node) {
        builder.invokeMethod("nodeCompleted", parent, node);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProjectProperties() {
        return (Map<String, Object>) getProject().invokeMethod("getProperties");
    }

    public DynamicObject getProject() {
        return DynamicObjectUtil.asDynamicObject(builder.invokeMethod("getProject"));
    }

    public void setSaveStreams(boolean value) {
        builder.invokeMethod("setSaveStreams", value);
    }

    /**
     * Masks a string against Ant property expansion.
     * This needs to be used when adding a File as a String property.
     *
     * @param string to mask
     *
     * @return The masked String
     */
    public static String maskFilename(String string) {
        return string.replaceAll("\\$", "\\$\\$");
    }

}
