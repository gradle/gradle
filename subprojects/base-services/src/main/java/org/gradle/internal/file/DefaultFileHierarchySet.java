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

package org.gradle.internal.file;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultFileHierarchySet {
    private static final EmptyFileHierarchySet EMPTY = new EmptyFileHierarchySet();

    /**
     * Creates an empty set.
     */
    public static FileHierarchySet of() {
        return EMPTY;
    }

    /**
     * Creates a set containing the given directory and all its descendants
     */
    public static FileHierarchySet of(final File rootDir) {
        return new PrefixFileSet(rootDir);
    }

    /**
     * Creates a set containing the given directories and all their descendants.
     */
    public static FileHierarchySet of(Collection<File> rootDirs) {
        FileHierarchySet set = EMPTY;
        for (File rootDir : rootDirs) {
            set = set.plus(rootDir);
        }
        return set;
    }

    private static class EmptyFileHierarchySet implements FileHierarchySet {
        @Override
        public boolean contains(File file) {
            return false;
        }

        @Override
        public boolean contains(String path) {
            return false;
        }

        @Override
        public FileHierarchySet plus(File rootDir) {
            return new PrefixFileSet(rootDir);
        }
    }

    private static class PrefixFileSet implements FileHierarchySet {
        private final Node rootNode;

        PrefixFileSet(File rootDir) {
            String path = toPath(rootDir);
            this.rootNode = new Node(path);
        }

        PrefixFileSet(Node rootNode) {
            this.rootNode = rootNode;
        }

        @VisibleForTesting
        List<String> flatten() {
            List<String> prefixes = new ArrayList<String>();
            rootNode.collect(0, prefixes);
            return prefixes;
        }

        @Override
        public boolean contains(String path) {
            return rootNode.contains(path, 0);
        }

        @Override
        public boolean contains(File file) {
            return rootNode.contains(file.getPath(), 0);
        }

        @Override
        public FileHierarchySet plus(File rootDir) {
            return new PrefixFileSet(rootNode.plus(toPath(rootDir)));
        }

        private String toPath(File rootDir) {
            assert rootDir.isAbsolute();
            String absolutePath = rootDir.getAbsolutePath();
            if (absolutePath.equals("/")) {
                absolutePath = "";
            } else if (absolutePath.endsWith(File.separator)) {
                absolutePath = absolutePath.substring(0, absolutePath.length() - 1);
            }
            return absolutePath;
        }
    }

    private static class Node {
        private final String prefix;
        private final List<Node> children;

        Node(String prefix) {
            this.prefix = prefix;
            this.children = ImmutableList.of();
        }

        public Node(String prefix, List<Node> children) {
            this.prefix = prefix;
            this.children = children;
        }

        Node plus(String path) {
            int maxPos = Math.min(prefix.length(), path.length());
            int prefixLen = sizeOfCommonPrefix(path, 0);
            if (prefixLen == maxPos) {
                if (prefix.length() == path.length()) {
                    // Path == prefix
                    if (children.isEmpty()) {
                        return this;
                    }
                    return new Node(path);
                }
                if (prefix.length() < path.length()) {
                    // Path is a descendant of this
                    if (children.isEmpty()) {
                        return this;
                    }
                    int startNextSegment = prefix.length() + 1;
                    List<Node> merged = new ArrayList<Node>(children.size() + 1);
                    boolean matched = false;
                    for (Node child : children) {
                        if (child.sizeOfCommonPrefix(path, startNextSegment) > 0) {
                            // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                            merged.add(child.plus(path.substring(startNextSegment)));
                            matched = true;
                        } else {
                            merged.add(child);
                        }
                    }
                    if (!matched) {
                        merged.add(new Node(path.substring(startNextSegment)));
                    }
                    return new Node(prefix, merged);
                } else {
                    // Path is an ancestor of this
                    return new Node(path);
                }
            }
            String commonPrefix = prefix.substring(0, prefixLen);
            Node newThis = new Node(prefix.substring(prefixLen + 1), children);
            Node sibling = new Node(path.substring(prefixLen + 1));
            return new Node(commonPrefix, ImmutableList.of(newThis, sibling));
        }

        /**
         * Does not include the file separator.
         */
        int sizeOfCommonPrefix(String path, int offset) {
            int pos = 0;
            int lastSeparator = 0;
            int maxPos = Math.min(prefix.length(), path.length() - offset);
            for (; pos < maxPos; pos++) {
                if (prefix.charAt(pos) != path.charAt(pos + offset)) {
                    break;
                }
                if (prefix.charAt(pos) == File.separatorChar) {
                    lastSeparator = pos;
                }
            }
            if (pos == maxPos) {
                if (prefix.length() == path.length() - offset) {
                    return pos;
                }
                if (pos < prefix.length() && prefix.charAt(pos) == File.separatorChar) {
                    return pos;
                }
                if (pos < path.length() - offset && path.charAt(pos + offset) == File.separatorChar) {
                    return pos;
                }
            }
            return lastSeparator;
        }

        boolean isChildOfOrThis(String filePath, int offset) {
            if (!filePath.regionMatches(offset, prefix, 0, prefix.length())) {
                return false;
            }
            int endThisSegment = offset + prefix.length();
            return endThisSegment == filePath.length() || filePath.charAt(endThisSegment) == File.separatorChar;
        }

        boolean contains(String filePath, int offset) {
            if (!isChildOfOrThis(filePath, offset)) {
                return false;
            }
            if (children.isEmpty()) {
                return true;
            }

            int startNextSegment = offset + prefix.length() + 1;
            for (Node child : children) {
                if (child.contains(filePath, startNextSegment)) {
                    return true;
                }
            }
            return false;
        }

        public void collect(int depth, List<String> prefixes) {
            if (depth == 0) {
                prefixes.add(prefix);
            } else {
                prefixes.add(depth + ":" + prefix.replace(File.separatorChar, '/'));
            }
            for (Node child : children) {
                child.collect(depth + 1, prefixes);
            }
        }
    }
}
