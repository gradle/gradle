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

package org.gradle.nativeplatform.toolchain.internal;

// This should be replaced by some stronger modelling and made open rather than hardcoding a set of source languages here
public enum NativeLanguage {
    // For legacy software model behaviour, which is to assume that any kind of runtime can be built when any compiler is available
    ANY() {
        @Override
        public String toString() {
            return "any language";
        }
    },
    CPP() {
        @Override
        public String toString() {
            return "C++";
        }
    },
    SWIFT() {
        @Override
        public String toString() {
            return "Swift";
        }
    };
}
