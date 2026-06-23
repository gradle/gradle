package com.acme;

import java.util.List;
import java.util.ArrayList;
import org.apache.commons.text.WordUtils;
import org.apache.commons.lang3.tuple.Pair;

// tag::sample[]
public class Simpsons {
    private static final Person HOMER = new Person("Homer", "Simpson");
    private static final Person MARGE = new Person("Marjorie", "Simpson");
    private static final Person BART = new Person("Bartholomew", "Simpson");
    private static final Person LISA = new Person("Elisabeth Marie", "Simpson");
    private static final Person MAGGIE = new Person("Margaret Eve", "Simpson");
    private static final List<Person> FAMILY = new ArrayList<Person>() {{
        add(HOMER);
        add(MARGE);
        add(BART);
        add(LISA);
        add(MAGGIE);
    }};

    public static Person homer() { return HOMER; }

    public static Person marge() { return MARGE; }

    public static Person bart() { return BART; }

    public static Person lisa() { return LISA; }

    public static Person maggie() { return MAGGIE; }

    // ...
//end::sample[]
    public static Person named(String firstName) {
        String capitalized = WordUtils.capitalizeFully(firstName);
        for (Person person : FAMILY) {
            if (capitalized.equals(person.getFirstName())) {
                return person;
            }
        }
        return null;
    }

    public static Person of(Pair<String, String> pair) {
        return new Person(pair.getLeft(), pair.getRight());
    }
}
