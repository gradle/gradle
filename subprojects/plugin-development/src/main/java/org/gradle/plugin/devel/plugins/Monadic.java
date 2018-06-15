/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugin.devel.plugins;

import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;

public class Monadic {

    public static <OUT, I1> Provider<OUT> combine(Provider<I1> first, Transformer<OUT, I1> transform) {
        return first.map(transform);
    }

    public static <OUT, I1, I2> Provider<OUT> combine(Provider<I1> first, Provider<I2> second, Transformer<OUT, Param2<I1, I2>> transformer) {
        return null;
    }

    public static <OUT, I1, I2, I3> Provider<OUT> combine(Provider<I1> first, Provider<I2> second, Provider<I3> third, Transformer<OUT, Param3<I1, I2, I3>> transformer) {
        return null;
    }

    public static <I1, I2, I3> Provider<Param3<I1, I2, I3>> combine(Provider<I1> first, Provider<I2> second, Provider<I3> third) {
        return null;
    }

    public static class Param2<I1, I2> {
        public final I1 first;
        public final I2 second;

        Param2(I1 first, I2 second) {
            this.first = first;
            this.second = second;
        }

        public I1 component1() {
            return first;
        }

        public I2 component2() {
            return second;
        }
    }

    public static class Param3<I1, I2, I3> extends Param2<I1, I2> {
        public final I3 third;

        Param3(I1 first, I2 second, I3 third) {
            super(first, second);
            this.third = third;
        }

        public I3 component3() {
            return third;
        }
    }
}
