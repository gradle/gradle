grammar Calculator;

@lexer::header {
    package org.gradle;
}

@parser::header {
    package org.gradle;
}

add
    :    NUMBER PLUS NUMBER
    ;

NUMBER
    :    ('0'..'9')+
    ;

PLUS
    :    ('+')
    ;


