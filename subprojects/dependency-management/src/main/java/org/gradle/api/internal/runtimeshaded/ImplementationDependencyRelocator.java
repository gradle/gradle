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

package org.gradle.api.internal.runtimeshaded;

import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ImplementationDependencyRelocator extends Remapper {

    private final Pattern classPattern = Pattern.compile("(\\[*)?L(.+)");
    private final Trie prefixes = readPrefixes();

    private static class Trie {
        private final char c;
        private final boolean terminal;
        private final Trie[] transitions;

        private Trie(char c, boolean terminal, Trie[] transitions) {
            this.c = c;
            this.terminal = terminal;
            this.transitions = transitions;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            return dump(sb);
        }

        private String dump(StringBuilder sb) {
            sb.append(c).append(terminal ? "(terminal)\n" : "\n");
            sb.append("Next: ");
            for (Trie transition : transitions) {
               sb.append(transition.c).append(" ");
            }
            sb.append("\n");
            return sb.toString();
        }

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
                    if (transition.c == c) {
                        cur = transition;
                        idx++;
                        found = true;
                        if (idx == seq.length()) {
                            return cur.terminal;
                        }
                        break;
                    } else if (transition.c > c) {
                        return false;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return cur.terminal;
        }

        private static class Builder {
            private final char c;

            private boolean terminal;
            private List<Builder> transitions = new ArrayList<Builder>();

            private Builder(char c) {
                this.c = c;
            }

            public Builder addTransition(char c, boolean terminal) {
                Builder b = null;
                for (Builder transition : transitions) {
                    if (transition.c == c) {
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

            public Trie build() {
                Trie[] transitions = new Trie[this.transitions.size()];
                for (int i = 0; i < this.transitions.size(); i++) {
                    Builder transition = this.transitions.get(i);
                    transitions[i] = transition.build();
                }
                Arrays.sort(transitions, new Comparator<Trie>() {
                    @Override
                    public int compare(Trie o1, Trie o2) {
                        return o1.c - o2.c;
                    }
                });
                return new Trie(c, terminal, transitions);
            }
        }
    }

    private static Trie readPrefixes() {
        final Trie.Builder builder = new Trie.Builder('\0');
        IoActions.withResource(ImplementationDependencyRelocator.class.getResourceAsStream("relocated.txt"), new ErroringAction<InputStream>() {
            @Override
            protected void doExecute(InputStream thing) throws Exception {
                BufferedReader reader = new BufferedReader(new InputStreamReader(thing, Charset.forName("UTF-8")));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.length() > 0) {
                        Trie.Builder cur = builder;
                        char[] chars = line.toCharArray();
                        for (int i = 0; i < chars.length; i++) {
                            char c = chars[i];
                            cur = cur.addTransition(c, i == chars.length - 1);
                        }
                    }
                }
            }
        });
        return builder.build();
    }

    public String map(String name) {
        String value = name;

        String prefix = "";

        Matcher m = classPattern.matcher(name);
        if (m.matches()) {
            prefix = m.group(1) + "L";
            name = m.group(2);
        }

        String relocated = maybeRelocateResource(name);
        if (relocated == null) {
            return value;
        } else {
            return prefix.concat(relocated);
        }
    }

    public String maybeRelocateResource(String resource) {
        if (prefixes.find(resource)) {
            return "org/gradle/internal/impldep/" + resource;
        }
        return null;
    }

    public boolean keepOriginalResource(String resource) {
        return resource == null || maybeRelocateResource(resource) == null
            || !resource.startsWith("com/sun/jna"); // in order to use a newer version of jna the resources must not be available in the old location
    }

    public ClassLiteralRemapping maybeRemap(String literal) {
        if (literal.startsWith("class$")) {
            String className = literal.substring(6).replace('$', '.');
            String replacement = maybeRelocateResource(className.replace('.', '/'));
            if (replacement == null) {
                return null;
            }
            String fieldNameReplacement = "class$" + replacement.replace('/', '$');
            return new ClassLiteralRemapping(className, replacement, fieldNameReplacement);
        }
        return null;
    }

    public static class ClassLiteralRemapping {
        private final String literal;
        private final String literalReplacement;
        private final String fieldNameReplacement;

        public ClassLiteralRemapping(String literal, String literalReplacement, String fieldNameReplacement) {
            this.literal = literal;
            this.literalReplacement = literalReplacement;
            this.fieldNameReplacement = fieldNameReplacement;
        }

        public String getLiteral() {
            return literal;
        }

        public String getLiteralReplacement() {
            return literalReplacement;
        }

        public String getFieldNameReplacement() {
            return fieldNameReplacement;
        }
    }

}
