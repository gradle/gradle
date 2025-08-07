/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.util.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.exceptions.MultiCauseException;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NullMarked
abstract public class MultiCauseExceptionUtil {
    public static List<Throwable> getCauses(Throwable parent) {
        ImmutableList.Builder<Throwable> causes = new ImmutableList.Builder<Throwable>();
        if (parent instanceof MultiCauseException) {
            causes.addAll(((MultiCauseException) parent).getCauses());
        } else if (parent.getCause() != null) {
            causes.add(parent.getCause());
        }
        return causes.build();
    }

    private static void getCausesInHierachy(Throwable parent, Set<Throwable> causes) {
        if (parent instanceof MultiCauseException) {
            List<? extends Throwable> exceptionCauses = ((MultiCauseException) parent).getCauses();
            for (Throwable exceptionCause : exceptionCauses) {
                if (!causes.contains(exceptionCause)) {
                    causes.add(exceptionCause);
                    getCausesInHierachy(exceptionCause, causes);
                }
            }
        } else {
            Throwable cause = parent.getCause();
            if (cause != null && !causes.contains(cause)) {
                causes.add(cause);
                getCausesInHierachy(cause, causes);
            }
        }
    }
    public static Collection<Throwable> getCausesInHierachy(Throwable parent) {
        Set<Throwable> causes = new HashSet<>();
        getCausesInHierachy(parent, causes);
        return causes;
    }
}
