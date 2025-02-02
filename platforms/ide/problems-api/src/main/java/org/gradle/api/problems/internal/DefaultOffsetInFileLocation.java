/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import com.google.common.base.Objects;
import org.gradle.api.problems.FileLocation;
import org.gradle.api.problems.OffsetInFileLocation;

public class DefaultOffsetInFileLocation extends DefaultFileLocation implements OffsetInFileLocation {

    private final int offset;
    private final int length;

    private DefaultOffsetInFileLocation(String path, int offset, int length) {
        super(path);
        this.offset = offset;
        this.length = length;
    }

    public static FileLocation from(String path, int offset, int length) {
        return new DefaultOffsetInFileLocation(path, offset, length);
    }


    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultOffsetInFileLocation)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        DefaultOffsetInFileLocation that = (DefaultOffsetInFileLocation) o;
        return offset == that.offset && length == that.length;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), offset, length);
    }
}
