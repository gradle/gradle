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

package org.gradle.ide.xcode.internal.xcodeproj;

import com.google.common.base.Preconditions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Information for building a specific artifact (a library, binary, or test).
 */
public abstract class PBXTarget extends PBXProjectItem {
    private final String name;
    private final ProductType productType;
    private final List<PBXBuildPhase> buildPhases;
    private final XCConfigurationList buildConfigurationList;
    @Nullable
    private String productName;
    @Nullable
    private PBXFileReference productReference;
    public PBXTarget(String name, ProductType productType) {
        this.name = Preconditions.checkNotNull(name);
        this.productType = Preconditions.checkNotNull(productType);
        this.buildPhases = new ArrayList<>();
        this.buildConfigurationList = new XCConfigurationList();
    }

    public String getName() {
        return name;
    }

    public ProductType getProductType() {
        return productType;
    }

    public List<PBXBuildPhase> getBuildPhases() {
        return buildPhases;
    }

    public XCConfigurationList getBuildConfigurationList() {
        return buildConfigurationList;
    }

    @Nullable
    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    @Nullable
    public PBXFileReference getProductReference() {
        return productReference;
    }

    public void setProductReference(PBXFileReference v) {
        productReference = v;
    }

    @Override
    public String isa() {
        return "PBXTarget";
    }

    @Override
    public int stableHash() {
        return name.hashCode();
    }

    @Override
    public void serializeInto(XcodeprojSerializer s) {
        super.serializeInto(s);

        s.addField("name", name);
        if (productType != null) {
            s.addField("productType", productType.toString());
        }
        if (productName != null) {
            s.addField("productName", productName);
        }
        if (productReference != null) {
            s.addField("productReference", productReference);
        }
        s.addField("buildPhases", buildPhases);
        if (buildConfigurationList != null) {
            s.addField("buildConfigurationList", buildConfigurationList);
        }
    }

    public enum ProductType {
        INDEXER("org.gradle.product-type.indexer"),

        STATIC_LIBRARY("com.apple.product-type.library.static"),
        DYNAMIC_LIBRARY("com.apple.product-type.library.dynamic"),
        TOOL("com.apple.product-type.tool"),
        BUNDLE("com.apple.product-type.bundle"),
        FRAMEWORK("com.apple.product-type.framework"),
        STATIC_FRAMEWORK("com.apple.product-type.framework.static"),
        APPLICATION("com.apple.product-type.application"),
        UNIT_TEST("com.apple.product-type.bundle.unit-test"),
        IN_APP_PURCHASE_CONTENT("com.apple.product-type.in-app-purchase-content"),
        APP_EXTENSION("com.apple.product-type.app-extension"),
        WATCH_OS1_APPLICATION("com.apple.product-type.application.watchapp"),
        WATCH_OS1_EXTENSION("com.apple.product-type.watchkit-extension");

        public final String identifier;

        ProductType(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public String toString() {
            return identifier;
        }
    }
}
