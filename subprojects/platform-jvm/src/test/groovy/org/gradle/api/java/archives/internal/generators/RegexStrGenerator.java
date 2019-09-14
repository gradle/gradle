/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.java.archives.internal.generators;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import dk.brics.automaton.State;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Generex does not always work (see https://github.com/mifmif/Generex/issues/52),
 * so this class implements regular expre
 */
public class RegexStrGenerator {
    private static final LoadingCache<String, CachedValue> REGEXPS =
        CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build(CacheLoader.from(p -> buildStateMap(new RegExp(p).toAutomaton())));

    static class CachedValue {
        private final AState initialState;
        private final RunAutomaton ra;

        CachedValue(AState initialState, RunAutomaton ra) {
            this.initialState = initialState;
            this.ra = ra;
        }
    }


    private final AState initialState;
    private final RunAutomaton ra;
    private final StringBuilder sb = new StringBuilder();
    private final String pattern;

    static class AState {
        final int id;
        final boolean accept;
        ATransition[] transitions;

        public AState(int id, State state) {
            this.id = id;
            this.accept = state.isAccept();
        }

        @Override
        public String toString() {
            return (accept ? "S" : "s") + id;
        }
    }

    static class ATransition {
        final char min;
        final char max;
        final AState to;

        ATransition(char min, char max, AState to) {
            this.min = min;
            this.max = max;
            this.to = to;
        }

        @Override
        public String toString() {
            return "{" + min + ".." + max + " => " + to + '}';
        }
    }

    public RegexStrGenerator(String pattern) {
        this.pattern = pattern;
        // Unfortunately, junit-quickcheck does not reuse generators, so
        // we cache the parsed regexps
        // https://github.com/pholser/junit-quickcheck/issues/238
        CachedValue cached = REGEXPS.getUnchecked(pattern);
        this.initialState = cached.initialState;
        this.ra = cached.ra;
    }

    public boolean matches(String value) {
        return ra.run(value);
    }

    @Override
    public String toString() {
        return "RegexStrGenerator{" +
            "pattern=" + pattern +
            ", state=" + sb.substring(0, Math.min(100, sb.length())) +
            '}';
    }

    /**
     * Unfortunately {@link State#getTransitions()} returns {@link Set},
     * and we can't easily pick elements from it. So we make a copy of the automaton.
     */
    private static CachedValue buildStateMap(Automaton automaton) {
        Set<State> allStates = automaton.getStates();
        Map<State, AState> stateMap = new HashMap<>();
        int id = 0;
        for (State state : allStates) {
            stateMap.put(state, new AState(id++, state));
        }

        for (Map.Entry<State, AState> entry : stateMap.entrySet()) {
            State oldState = entry.getKey();
            AState newState = entry.getValue();
            // The order of transitions is not important
            newState.transitions =
                oldState.getTransitions()
                    .stream()
                    .map(t -> new ATransition(t.getMin(), t.getMax(), stateMap.get(t.getDest())))
                    .toArray(ATransition[]::new);
        }
        State initialState = automaton.getInitialState();
        return new CachedValue(stateMap.get(initialState), new RunAutomaton(automaton));
    }

    public String random(SourceOfRandomness random, int minLength, int maxLength) {
        for (int i = 0; i < 10; i++) {
            int len = random.nextInt(minLength, maxLength);
            String value = random(random, len);
            if (value.length() <= maxLength) {
                return value;
            }
            // Oh, no. The string turned out to be too long. Let's retry
        }
        return random(random, minLength);
    }

    public String random(SourceOfRandomness random, int minLength) {
        sb.setLength(0);
        AState state = initialState;
        // Note: there's no dead states, so we can pick any transition
        // We limit the number of attempts just in case
        for (int i = 0; i < 100000; i++) {
            // transitions.length == 0 when no further progress can be made
            // For instance: "a+b" might generate aaab. Then it can't add more characters,
            if (state.accept && (sb.length() >= minLength || state.transitions.length == 0)) {
                return sb.toString();
            }
            ATransition[] transitions = state.transitions;
            ATransition transition;
            if (transitions.length == 1) {
                transition = transitions[0];
            } else {
                transition = random.choose(transitions);
            }
            char c;
            if (transition.min == transition.max) {
                c = transition.min;
            } else {
                c = random.nextChar(transition.min, transition.max);
            }
            sb.append(c);
            state = transition.to;
        }
        throw new IllegalStateException("Unable to generate string for regexp " + pattern +
            ", current generated prefix is " + sb.substring(0, Math.min(50, sb.length())));
    }
}
