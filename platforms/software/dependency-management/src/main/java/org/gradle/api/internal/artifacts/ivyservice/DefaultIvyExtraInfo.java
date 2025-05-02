/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.base.Joiner;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ivy.IvyExtraInfo;
import org.gradle.util.internal.CollectionUtils;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultIvyExtraInfo implements IvyExtraInfo {
    protected final Map<NamespaceId, String> extraInfo;

    public DefaultIvyExtraInfo() {
        this.extraInfo = new LinkedHashMap<>();
    }

    public DefaultIvyExtraInfo(Map<NamespaceId, String> extraInfo) {
        this.extraInfo = extraInfo;
    }

    @Override
    public String get(String name) {
        List<Map.Entry<NamespaceId, String>> foundEntries = new ArrayList<>();
        for (Map.Entry<NamespaceId, String> entry : extraInfo.entrySet()) {
            if (entry.getKey().getName().equals(name)) {
                foundEntries.add(entry);
            }
        }
        if (foundEntries.size() > 1) {
            String allNamespaces = Joiner.on(", ").join(CollectionUtils.collect(foundEntries, original -> original.getKey().getNamespace()));
            throw new InvalidUserDataException(String.format("Cannot get extra info element named '%s' by name since elements with this name were found from multiple namespaces (%s).  Use get(String namespace, String name) instead.", name, allNamespaces));
        }
        return foundEntries.size() == 0 ? null : foundEntries.get(0).getValue();
    }

    @Override
    public String get(String namespace, String name) {
        return extraInfo.get(new NamespaceId(namespace, name));
    }

    @Override
    public Map<QName, String> asMap() {
        Map<QName, String> map = new LinkedHashMap<>();
        for (Map.Entry<NamespaceId, String> entry : extraInfo.entrySet()) {
            map.put(new QName(entry.getKey().getNamespace(), entry.getKey().getName()), entry.getValue());
        }
        return Collections.unmodifiableMap(map);
    }
}
