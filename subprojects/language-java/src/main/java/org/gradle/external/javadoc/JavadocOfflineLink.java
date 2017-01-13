/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.external.javadoc;

import com.google.common.base.Objects;

import java.io.Serializable;

/**
 * This class is used to hold the information that can be provided to the javadoc executable via the -linkoffline
 * option.
 */
public class JavadocOfflineLink implements Serializable {
    private final String extDocUrl;
    private final String packagelistLoc;

    public JavadocOfflineLink(String extDocUrl, String packagelistLoc) {
        this.extDocUrl = extDocUrl;
        this.packagelistLoc = packagelistLoc;
    }

    public String getExtDocUrl() {
        return extDocUrl;
    }

    public String getPackagelistLoc() {
        return packagelistLoc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JavadocOfflineLink that = (JavadocOfflineLink) o;
        return Objects.equal(extDocUrl, that.extDocUrl)
            && Objects.equal(packagelistLoc, that.packagelistLoc);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(extDocUrl, packagelistLoc);
    }

    public String toString() {
        return extDocUrl + "' '" + packagelistLoc;
    }
}
