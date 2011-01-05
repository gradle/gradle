header {
    package org.gradle;
}

class CalculatorLexer extends Lexer; 

NUMBER	:	('0'..'9')+;
PLUS	:	'+';

class CalculatorParser extends Parser;

add	:	NUMBER PLUS NUMBER;
