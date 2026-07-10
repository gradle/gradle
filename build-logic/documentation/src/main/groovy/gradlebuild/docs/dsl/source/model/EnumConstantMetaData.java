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

package gradlebuild.docs.dsl.source.model;

import java.io.Serializable;

public class EnumConstantMetaData extends AbstractLanguageElement implements Serializable {
    private final String name;
    private final ClassMetaData ownerClass;

    public EnumConstantMetaData(String name, ClassMetaData ownerClass) {
        this.name = name;
        this.ownerClass = ownerClass;
    }

    public String getName() {
        return name;
    }

    public ClassMetaData getOwnerClass() {
        return ownerClass;
    }

    @Override
    public String toString() {
        return String.format("%s.%s()", ownerClass, name);
    }

}
