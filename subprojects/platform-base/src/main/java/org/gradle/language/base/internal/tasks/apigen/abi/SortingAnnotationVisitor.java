/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.base.internal.tasks.apigen.abi;

import com.google.common.collect.Lists;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

public class SortingAnnotationVisitor extends AnnotationVisitor {
    private final AnnotationSig sig;
    SortingAnnotationVisitor parent;
    String array;
    String subAnnName;
    final List<AnnotationValue> values = Lists.newLinkedList();

    public SortingAnnotationVisitor(AnnotationSig parent, AnnotationVisitor av) {
        super(Opcodes.ASM5, av);
        this.sig = parent;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
        AnnotationSig subAnn = new AnnotationSig(desc, true);
        SortingAnnotationVisitor sortingAnnotationVisitor = new SortingAnnotationVisitor(subAnn, super.visitAnnotation(name, desc));
        sortingAnnotationVisitor.subAnnName = name == null ? "value" : name;
        sortingAnnotationVisitor.parent = this;
        return sortingAnnotationVisitor;
    }

    @Override
    public void visit(String name, Object value) {
        values.add(new SimpleAnnotationValue(name, value));
        super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        SortingAnnotationVisitor sortingAnnotationVisitor = new SortingAnnotationVisitor(sig, super.visitArray(name));
        sortingAnnotationVisitor.array = name;
        return sortingAnnotationVisitor;
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
        values.add(new EnumAnnotationValue(name == null ? "value" : name, desc, value));
        super.visitEnum(name, desc, value);
    }

    @Override
    public void visitEnd() {
        if (subAnnName != null) {
            AnnotationAnnotationValue ann = new AnnotationAnnotationValue(subAnnName, sig);
            parent.values.add(ann);
            subAnnName = null;
        } else if (array != null) {
            ArrayAnnotationValue arr = new ArrayAnnotationValue(array, values.toArray(new AnnotationValue[values.size()]));
            sig.getValues().add(arr);
            array = null;
        }
        sig.getValues().addAll(values);
        values.clear();
        super.visitEnd();
    }
}
