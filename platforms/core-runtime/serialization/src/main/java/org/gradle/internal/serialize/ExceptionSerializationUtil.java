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

package org.gradle.internal.serialize;

import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.MultiCauseException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Static util class containing helper methods for exception serialization.
 */
public final class ExceptionSerializationUtil {
    // It would be nice to use Guava's immutable collections here, if we could get them on the proper classpath
    public static final Set<String> CANDIDATE_GET_CAUSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("getCauses", "getFailures")));

    private ExceptionSerializationUtil() {
        // Can't instantiate static util class
    }

    public static List<? extends Throwable> extractCauses(Throwable throwable) {
        if (throwable instanceof MultiCauseException) {
            return ((MultiCauseException) throwable).getCauses();
        } else {
            List<? extends Throwable> causes = tryExtractMultiCauses(throwable);
            if (causes != null) {
                return causes;
            }
            Throwable causeTmp;
            try {
                causeTmp = throwable.getCause();
            } catch (Throwable ignored) {
                // TODO:ADAM - switch the logging back on.
                //                LOGGER.debug("Ignoring failure to extract throwable cause.", ignored);
                causeTmp = null;
            }
            return causeTmp == null ? Collections.emptyList() : Collections.singletonList(causeTmp);
        }
    }

    /**
     * Does best effort to find a method which potentially returns multiple causes
     * for an exception. This is for classes of external projects which actually do
     * something similar to what we do in Gradle with {@link DefaultMultiCauseException}.
     * It is, in particular, the case for opentest4j.
     */
    private static List<? extends Throwable> tryExtractMultiCauses(Throwable throwable) {
        Method causesMethod = findCandidateGetCausesMethod(throwable);
        if (causesMethod != null) {
            Collection<?> causes;
            try {
                causes = Cast.uncheckedCast(causesMethod.invoke(throwable));
            } catch (IllegalAccessException | InvocationTargetException e) {
                return null;
            }
            if (causes == null || causes.isEmpty()) {
                return null;
            }
            for (Object cause : causes) {
                if (!(cause instanceof Throwable)) {
                    return null;
                }
            }
            List<Throwable> result = new ArrayList<Throwable>(causes.size());
            for (Object cause : causes) {
                result.add(Cast.<Throwable>uncheckedCast(cause));
            }
            return result;
        }
        return null;
    }

    private static Method findCandidateGetCausesMethod(Throwable throwable) {
        Method[] declaredMethods = throwable.getClass().getDeclaredMethods();
        for (Method method : declaredMethods) {
            if (CANDIDATE_GET_CAUSES.contains(method.getName())) {
                Class<?> returnType = method.getReturnType();
                if (Collection.class.isAssignableFrom(returnType)) {
                    return method;
                }
            }
        }
        return null;
    }
}
