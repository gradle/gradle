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
package org.gradle.api.internal.artifacts.failures;

import org.gradle.api.artifacts.failures.AttemptedLocationVisitor;
import org.gradle.api.artifacts.failures.ResolutionFailureVisitor;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;

import java.util.List;

/**
 * Wraps logic of specific visitors.
 */
public abstract class ResolutionFailureVisitorSupport {
    static void extractAttemptedLocations(ResolutionFailureVisitor visitor, Throwable problem) {
        if (visitor instanceof AttemptedLocationVisitor) {
            if (problem instanceof ModuleVersionNotFoundException) {
                AttemptedLocationVisitor attemptedLocationVisitor = (AttemptedLocationVisitor) visitor;
                List<String> attemptedLocations = ((ModuleVersionNotFoundException) problem).getAttemptedLocations();
                for (String attemptedLocation : attemptedLocations) {
                    attemptedLocationVisitor.visitAttemptedLocation(attemptedLocation);
                }
            }
        }
    }
}
