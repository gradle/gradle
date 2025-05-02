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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

import static org.gradle.nativeplatform.fixtures.app.SourceFileElement.*

/**
 * An app that uses a library with 2 dependencies, one an API dependency and the other an implementation dependency.
 */
class CppAppWithLibrariesWithApiDependencies implements AppElement {
    final SourceFileElement main = new SourceFileElement() {
        final SourceFile sourceFile = new SourceFile("cpp", "main.cpp", """
#include <iostream>
#include "deck.h"

int main() {
    Deck deck;
    deck.shuffle();
    Card& card = deck.draw();
    std::cout << card.getName() << std::endl;
    return 0;
}
""")
    }

    final CppSourceFileElement deck = new CppSourceFileElement() {
        final SourceFileElement header = ofFile(new SourceFile("public", "deck.h", """
#include "card.h"
#ifdef _WIN32
#define EXPORT_FUNC __declspec(dllexport)
#else
#define EXPORT_FUNC
#endif

class EXPORT_FUNC Deck {
    Card card;
public:
    void shuffle();
    Card& draw();
};
"""))
        final SourceFileElement source = ofFile(new SourceFile("cpp", "deck.cpp", """
#include "deck.h"
#include "shuffle.h"

void Deck::shuffle() {
    ShuffleAlgorithm shuffler;
    shuffler.shuffle();
}

Card& Deck::draw() {
    return card;
}
"""))
    }

    def card = new CppSourceFileElement() {
        final SourceFileElement header = ofFile(new SourceFile("public", "card.h", """
#include <string>
#ifdef _WIN32
#define EXPORT_FUNC __declspec(dllexport)
#else
#define EXPORT_FUNC
#endif

class EXPORT_FUNC Card {
    std::string name;
public:
    Card();
    std::string& getName();
};
"""))
        final SourceFileElement source = ofFile(new SourceFile("cpp", "card.cpp", """
#include "card.h"

Card::Card() {
    name = "ace of spades";
}

std::string&
Card::getName() {
    return name;
}
"""))
    }

    def shuffle = new CppSourceFileElement() {
        final SourceFileElement header = ofFile(new SourceFile("public", "shuffle.h", """
#ifdef _WIN32
#define EXPORT_FUNC __declspec(dllexport)
#else
#define EXPORT_FUNC
#endif

class EXPORT_FUNC ShuffleAlgorithm {
public:
    void shuffle();
};
"""))
        final SourceFileElement source = ofFile(new SourceFile("cpp", "shuffle.cpp", """
#include "shuffle.h"

void ShuffleAlgorithm::shuffle() {
}
"""))
    }

    @Override
    String getExpectedOutput() {
        return "ace of spades\n"
    }
}
