/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility.rules;

import gradlebuild.binarycompatibility.metadata.KotlinMetadataQueries;
import gradlebuild.binarycompatibility.sources.SinceTagStatus;
import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiConstructor;
import japicmp.model.JApiField;
import japicmp.model.JApiHasAnnotations;
import japicmp.model.JApiMethod;
import me.champeau.gradle.japicmp.report.Violation;

import java.util.Map;

public class SinceAnnotationRule extends AbstractGradleViolationRule {

    public static final String SINCE_ERROR_MESSAGE = "Is not annotated with @since ";
    public static final String SINCE_MISMATCH_ERROR_MESSAGE = "Has invalid @since: it should be %s, but currently is %s";
    public static final String SINCE_INCONSISTENT_ERROR_MESSAGE = "Has inconsistent @since: %s";

    private final boolean sinceRequired;

    public SinceAnnotationRule(Map<String, Object> params) {
        super(params);
        this.sinceRequired = params.get("sinceRequired") == null || (Boolean) params.get("sinceRequired");
    }

    @Override
    public Violation maybeViolation(final JApiCompatibility member) {

        if (shouldSkipViolationCheckFor(member)) {
            return null;
        }

        SinceTagStatus since = getRepository().getSince(member);
        if (since instanceof SinceTagStatus.Present present) {
            if (present.getVersion().equals(getCurrentVersion())) {
                return null;
            }
            return reject(member, String.format(SINCE_MISMATCH_ERROR_MESSAGE, getCurrentVersion(), present.getVersion()));
        } else if (since instanceof SinceTagStatus.Inconsistent inconsistent) {
            return reject(member, String.format(SINCE_INCONSISTENT_ERROR_MESSAGE, inconsistent.getVersions()));
        } else if (since instanceof SinceTagStatus.Missing) {
            return sinceRequired ? reject(member, SINCE_ERROR_MESSAGE + getCurrentVersion()) : null;
        } else {
            throw new IllegalStateException("Unknown status: " + since);
        }
    }

    private Violation reject(JApiCompatibility member, String message) {
        Violation error = Violation.error(member, message);
        return sinceRequired ? acceptOrReject(member, error) : error;
    }

    private boolean shouldSkipViolationCheckFor(JApiCompatibility member) {
        return !isClassFieldConstructorOrMethod(member) ||
            isInject(member) ||
            isOverrideMethod(member) ||
            isKotlinFileFacadeClass(member) ||
            getRepository().isGenerated(member);
    }

    private boolean isClassFieldConstructorOrMethod(JApiCompatibility member) {
        return member instanceof JApiClass || member instanceof JApiField || member instanceof JApiConstructor || member instanceof JApiMethod;
    }

    private boolean isInject(JApiCompatibility member) {
        return member instanceof JApiHasAnnotations && isInject((JApiHasAnnotations) member);
    }

    private boolean isOverrideMethod(JApiCompatibility member) {
        return member instanceof JApiMethod && isOverride((JApiMethod) member);
    }

    /**
     * Kotlin file-facade classes can't have kdoc comments.
     */
    private boolean isKotlinFileFacadeClass(JApiCompatibility member) {
        return member instanceof JApiClass && ((JApiClass) member).getNewClass().isPresent() && KotlinMetadataQueries.INSTANCE.isKotlinFileFacadeClass(((JApiClass) member).getNewClass().get());
    }
}
