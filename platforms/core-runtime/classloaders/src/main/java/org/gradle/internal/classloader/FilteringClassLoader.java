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

package org.gradle.internal.classloader;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A ClassLoader which hides all non-system classes, packages and resources. Allows certain non-system packages and classes to be declared as visible. By default, only the Java system classes,
 * packages and resources are visible.
 */
public class FilteringClassLoader extends ClassLoader implements ClassLoaderHierarchy {
    private static final ClassLoader EXT_CLASS_LOADER;
    private static final Set<String> SYSTEM_PACKAGES = new HashSet<String>();
    public static final String DEFAULT_PACKAGE = "DEFAULT";
    private final Set<String> packageNames;
    private final TrieSet packagePrefixes;
    private final TrieSet resourcePrefixes;
    private final Set<String> resourceNames;
    private final Set<String> classNames;
    private final Set<String> disallowedClassNames;
    private final TrieSet disallowedPackagePrefixes;

    static {
        EXT_CLASS_LOADER = ClassLoaderUtils.getPlatformClassLoader();
        for (Package p : new RetrieveSystemPackagesClassLoader(EXT_CLASS_LOADER).getPackages()) {
            SYSTEM_PACKAGES.add(p.getName());
        }
        try {
            //noinspection Since15
            ClassLoader.registerAsParallelCapable();
        } catch (NoSuchMethodError ignore) {
            // Not supported on Java 6
        }
    }

    private static class RetrieveSystemPackagesClassLoader extends ClassLoader {
        RetrieveSystemPackagesClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Package[] getPackages() {
            return super.getPackages();
        }
    }

    public FilteringClassLoader(ClassLoader parent, Spec spec) {
        super(parent);
        packageNames = new HashSet<String>(spec.packageNames);
        packagePrefixes = new TrieSet(spec.packagePrefixes);
        resourceNames = new HashSet<String>(spec.resourceNames);
        resourcePrefixes = new TrieSet(spec.resourcePrefixes);
        classNames = new HashSet<String>(spec.classNames);
        disallowedClassNames = new HashSet<String>(spec.disallowedClassNames);
        disallowedPackagePrefixes = new TrieSet(spec.disallowedPackagePrefixes);
    }

    @Override
    public void visit(ClassLoaderVisitor visitor) {
        visitor.visitSpec(new Spec(classNames, packageNames, packagePrefixes, resourcePrefixes, resourceNames, disallowedClassNames, disallowedPackagePrefixes));
        visitor.visitParent(getParent());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return EXT_CLASS_LOADER.loadClass(name);
        } catch (ClassNotFoundException ignore) {
            // ignore
        }

        if (!classAllowed(name)) {
            throw new ClassNotFoundException(name + " not found.");
        }

        Class<?> cl = super.loadClass(name, false);
        if (resolve) {
            resolveClass(cl);
        }

        return cl;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    @Override
    protected Package getPackage(String name) {
        Package p = super.getPackage(name);
        if (p == null || !isPackageAllowed(p.getName())) {
            return null;
        }
        return p;
    }

    @Override
    protected Package[] getPackages() {
        List<Package> packages = new ArrayList<Package>();
        for (Package p : super.getPackages()) {
            if (isPackageAllowed(p.getName())) {
                packages.add(p);
            }
        }
        return packages.toArray(new Package[0]);
    }

    @Override
    public URL getResource(String name) {
        if (isResourceAllowed(name)) {
            return super.getResource(name);
        }
        return EXT_CLASS_LOADER.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (isResourceAllowed(name)) {
            return super.getResources(name);
        }
        return EXT_CLASS_LOADER.getResources(name);
    }

    @Override
    public String toString() {
        return FilteringClassLoader.class.getSimpleName() + "(" + getParent() + ")";
    }

    private boolean isResourceAllowed(String resourceName) {
        return resourceNames.contains(resourceName)
            || resourcePrefixes.find(resourceName);
    }

    private boolean isPackageAllowed(String packageName) {
        if (disallowedPackagePrefixes.find(packageName)) {
            return false;
        }

        return SYSTEM_PACKAGES.contains(packageName)
            || packageNames.contains(packageName)
            || packagePrefixes.find(packageName);
    }

    private boolean classAllowed(String className) {
        if (disallowedClassNames.contains(className)) {
            return false;
        }

        if (classNames.contains(className)) {
            return true;
        }

        if (disallowedPackagePrefixes.find(className)) {
            return false;
        }

        return packagePrefixes.find(className)
            || (packagePrefixes.contains(DEFAULT_PACKAGE + ".") && isInDefaultPackage(className));
    }

    private static boolean isInDefaultPackage(String className) {
        return !className.contains(".");
    }

    public static class Spec extends ClassLoaderSpec {

        final Set<String> packageNames = new HashSet<String>();
        final Set<String> packagePrefixes = new HashSet<String>();
        final Set<String> resourcePrefixes = new HashSet<String>();
        final Set<String> resourceNames = new HashSet<String>();
        final Set<String> classNames = new HashSet<String>();
        final Set<String> disallowedClassNames = new HashSet<String>();
        final Set<String> disallowedPackagePrefixes = new HashSet<String>();

        public Spec() {
        }

        public Spec(Spec spec) {
            this(
                spec.classNames,
                spec.packageNames,
                spec.packagePrefixes,
                spec.resourcePrefixes,
                spec.resourceNames,
                spec.disallowedClassNames,
                spec.disallowedPackagePrefixes
            );
        }

        public Spec(Iterable<String> classNames, Iterable<String> packageNames, Iterable<String> packagePrefixes, Iterable<String> resourcePrefixes, Iterable<String> resourceNames, Iterable<String> disallowedClassNames, Iterable<String> disallowedPackagePrefixes) {
            addAll(this.classNames, classNames);
            addAll(this.packageNames, packageNames);
            addAll(this.packagePrefixes, packagePrefixes);
            addAll(this.resourcePrefixes, resourcePrefixes);
            addAll(this.resourceNames, resourceNames);
            addAll(this.disallowedClassNames, disallowedClassNames);
            addAll(this.disallowedPackagePrefixes, disallowedPackagePrefixes);
        }

        private static void addAll(Collection<String> collection, Iterable<String> elements) {
            for (String element : elements) {
                collection.add(element);
            }
        }

        /**
         * Whether or not any constraints have been added to this filter.
         *
         * @return true if no constraints have been added
         */
        public boolean isEmpty() {
            return classNames.isEmpty()
                && packageNames.isEmpty()
                && packagePrefixes.isEmpty()
                && resourcePrefixes.isEmpty()
                && resourceNames.isEmpty()
                && disallowedClassNames.isEmpty()
                && disallowedPackagePrefixes.isEmpty();
        }

        /**
         * Marks a package and all its sub-packages as visible. Also makes resources in those packages visible.
         *
         * @param packageName the package name
         */
        public void allowPackage(String packageName) {
            packageNames.add(packageName);
            packagePrefixes.add(packageName + ".");
            resourcePrefixes.add(packageName.replace('.', '/') + '/');
        }

        /**
         * Marks a single class as visible.
         *
         * @param clazz the class
         */
        public void allowClass(Class<?> clazz) {
            classNames.add(clazz.getName());
        }

        /**
         * Marks a single class as not visible.
         *
         * @param className the class name
         */
        public void disallowClass(String className) {
            disallowedClassNames.add(className);
        }

        /**
         * Marks a package and all its sub-packages as not visible. Does not affect resources in those packages.
         *
         * @param packagePrefix the package prefix
         */
        public void disallowPackage(String packagePrefix) {
            disallowedPackagePrefixes.add(packagePrefix + ".");
        }

        /**
         * Marks all resources with the given prefix as visible.
         *
         * @param resourcePrefix the resource prefix
         */
        public void allowResources(String resourcePrefix) {
            resourcePrefixes.add(resourcePrefix + "/");
        }

        /**
         * Marks a single resource as visible.
         *
         * @param resourceName the resource name
         */
        public void allowResource(String resourceName) {
            resourceNames.add(resourceName);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            Spec other = (Spec) obj;
            return other.packageNames.equals(packageNames)
                && other.packagePrefixes.equals(packagePrefixes)
                && other.resourceNames.equals(resourceNames)
                && other.resourcePrefixes.equals(resourcePrefixes)
                && other.classNames.equals(classNames)
                && other.disallowedClassNames.equals(disallowedClassNames)
                && other.disallowedPackagePrefixes.equals(disallowedPackagePrefixes);
        }

        @Override
        public int hashCode() {
            return packageNames.hashCode()
                ^ packagePrefixes.hashCode()
                ^ resourceNames.hashCode()
                ^ resourcePrefixes.hashCode()
                ^ classNames.hashCode()
                ^ disallowedClassNames.hashCode()
                ^ disallowedPackagePrefixes.hashCode();
        }

        public Set<String> getPackageNames() {
            return packageNames;
        }

        public Set<String> getPackagePrefixes() {
            return packagePrefixes;
        }

        public Set<String> getResourcePrefixes() {
            return resourcePrefixes;
        }

        public Set<String> getResourceNames() {
            return resourceNames;
        }

        public Set<String> getClassNames() {
            return classNames;
        }

        public Set<String> getDisallowedClassNames() {
            return disallowedClassNames;
        }

        public Set<String> getDisallowedPackagePrefixes() {
            return disallowedPackagePrefixes;
        }
    }

    public static class TrieSet implements Iterable<String> {
        private final Trie trie;
        private final Set<String> set;

        public TrieSet(Collection<String> words) {
            this.trie = Trie.from(words);
            this.set = new HashSet<String>(words);
        }

        public boolean find(CharSequence seq) {
            return trie.find(seq);
        }

        public boolean contains(String seq) {
            return set.contains(seq);
        }

        @NonNull
        @Override
        public Iterator<String> iterator() {
            return set.iterator();
        }
    }

    /**
     * Move the Trie class in :base-services to :functional once we can use Java 8 and delete this class.
     */
    public static class Trie implements Comparable<Trie> {
        private final char chr;
        private final boolean terminal;
        private final Trie[] transitions;

        public static Trie from(Iterable<String> words) {
            Builder builder = new Builder();
            for (String word : words) {
                builder.addWord(word);
            }
            return builder.build();
        }

        private Trie(char chr, boolean terminal, Trie[] transitions) {
            this.chr = chr;
            this.terminal = terminal;
            this.transitions = transitions;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(chr).append(terminal ? "(terminal)\n" : "\n");
            sb.append("Next: ");
            for (Trie transition : transitions) {
                sb.append(transition.chr).append(" ");
            }
            sb.append("\n");
            return sb.toString();
        }

        @Override
        public int compareTo(@NonNull Trie o) {
            return chr - o.chr;
        }

        /**
         * Checks if the trie contains the given character sequence or any prefixes of the sequence.
         */
        public boolean find(CharSequence seq) {
            if (seq.length() == 0) {
                return false;
            }
            int idx = 0;
            Trie cur = this;
            while (idx < seq.length()) {
                char c = seq.charAt(idx);
                boolean found = false;
                for (Trie transition : cur.transitions) {
                    if (transition.chr == c) {
                        cur = transition;
                        idx++;
                        found = true;
                        if (idx == seq.length()) {
                            return cur.terminal;
                        }
                        break;
                    } else if (transition.chr > c) {
                        return false;
                    }
                }
                if (!found) {
                    return cur.terminal;
                }
            }
            return cur.terminal;
        }

        public static class Builder {
            private final char chr;
            private final List<Builder> transitions = new ArrayList<Builder>();

            private boolean terminal;

            public Builder() {
                chr = '\0';
            }

            private Builder(char chr) {
                this.chr = chr;
            }

            private Builder addTransition(char c, boolean terminal) {
                Builder b = null;
                for (Builder transition : transitions) {
                    if (transition.chr == c) {
                        b = transition;
                        break;
                    }
                }
                if (b == null) {
                    b = new Builder(c);
                    transitions.add(b);
                }
                b.terminal |= terminal;
                return b;
            }

            public void addWord(String word) {
                Trie.Builder cur = this;
                char[] chars = word.toCharArray();
                for (int i = 0; i < chars.length; i++) {
                    char c = chars[i];
                    cur = cur.addTransition(c, i == chars.length - 1);
                }
            }

            public Trie build() {
                Trie[] transitions = new Trie[this.transitions.size()];
                for (int i = 0; i < this.transitions.size(); i++) {
                    Builder transition = this.transitions.get(i);
                    transitions[i] = transition.build();
                }
                Arrays.sort(transitions);
                return new Trie(chr, terminal, transitions);
            }
        }
    }

    public interface Action<T> {
        void execute(T target);
    }
}
