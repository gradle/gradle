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

import org.gradle.api.Action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

class Trie {
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
        return toString(sb);
    }

    private String toString(StringBuilder sb) {
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
                return cur.terminal;
            }
        }
        return cur.terminal;
    }

    public void dump(boolean all, Action<? super String> onWord) {
        dump(new StringBuilder(), all, this, onWord);
    }

    private void dump(StringBuilder buffer, boolean all, Trie trie, Action<? super String> onWord) {
        for (Trie transition : trie.transitions) {
            buffer.append(transition.c);
            if (transition.terminal) {
                onWord.execute(buffer.toString());
                if (all) {
                    dump(buffer, true, transition, onWord);
                }
            } else {
                dump(buffer, all, transition, onWord);
            }
            buffer.setLength(buffer.length() - 1);
        }
    }

    public static class Builder {
        private final char c;

        private boolean terminal;
        private List<Builder> transitions = new ArrayList<Builder>();

        public Builder() {
            c = '\0';
        }

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
