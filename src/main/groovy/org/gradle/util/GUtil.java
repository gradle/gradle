/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.util;

import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class GUtil {
    public static List flatten(Object[] elements, List addTo) {
        return flatten(Arrays.asList(elements), addTo);
    }
    
    public static List flatten(Collection elements, List addTo) {
        Iterator iter = elements.iterator();
        while (iter.hasNext()) {
            Object element = iter.next();
            if (element instanceof Collection) {
                flatten((Collection) element, addTo);
            } else if (element instanceof Map) {
                flatten(((Map) element).values(), addTo);
            } else {
                addTo.add(element);
            }
        }
        return addTo;
    }

    public static List flatten(Collection elements) {
        return flatten(elements, new ArrayList());
    }

    public static String join(Collection self, String separator) {
        StringBuffer buffer = new StringBuffer();
        boolean first = true;

        if (separator == null) separator = "";

        for (Iterator iter = self.iterator(); iter.hasNext();) {
            Object value = iter.next();
            if (first) {
                first = false;
            } else {
                buffer.append(separator);
            }
            buffer.append(value.toString());
        }
        return buffer.toString();
    }

    public static String join(Object[] self, String separator) {
        return join(Arrays.asList(self), separator);
    }

    public static boolean isTrue(Object object) {
        if (object == null) {
            return false;
        }
        if (object instanceof Collection) {
            return ((Collection) object).size() > 0;
        } else if (object instanceof String) {
            return ((String) object).length() > 0;
        }
        return true;
    }

    public static <T> T elvis(T object, T defaultValue) {
        return isTrue(object) ? object : defaultValue;
    }

    public static Set addToSet(Set source, Collection setToAdd) {
        source.addAll(setToAdd);
        return source;
    }

    public static Set addSets(Set set1, Set set2) {
        HashSet set = new HashSet();
        set.addAll(set1);
        set.addAll(set2);
        return set;
    }

    public static List addLists(List list1, List list2) {
        ArrayList list = new ArrayList();
        list.addAll(list1);
        list.addAll(list2);
        return list;
    }

    public static Map addMaps(Map map1, Map map2) {
        HashMap map = new HashMap();
        map.putAll(map1);
        map.putAll(map2);
        return map;
    }

    public static Properties createProperties(File propertyFile) {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propertyFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    public static <T> List<T> chooseCollection(List<T> taskCollection, List<T> conventionCollection) {
        if (taskCollection != null) {
            return taskCollection;
        }
        if (conventionCollection != null) {
            taskCollection = conventionCollection;
        } else {
            taskCollection = new ArrayList<T>();
        }
        return taskCollection;
    }

    public static Map map(Object... objects) {
        Map map = new HashMap();
        assert objects.length % 2 == 0;
        for (int i = 0; i < objects.length; i += 2) {
            map.put(objects[i], objects[i + 1]);
        }
        return map;
    }

}
