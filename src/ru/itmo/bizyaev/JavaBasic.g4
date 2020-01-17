grammar JavaBasic;

compilationUnit: typeDeclaration* EOF;

typeDeclaration
    : modifier*
      classDeclaration
    | ';'
    ;

classDeclaration
    : CLASS IDENTIFIER
      classBody
    ;

classBody: '{' classBodyDeclaration* '}';

classBodyDeclaration
    : ';'                           #emptyClassBodyDecl
    | STATIC? block                 #blockClassBodyDecl
    | modifier* memberDeclaration   #memberClassBodyDecl
    ;

memberDeclaration
    : methodDeclaration
    | fieldDeclaration
    | constructorDeclaration
    | classDeclaration
    ;

methodDeclaration
    : (typeType | VOID) IDENTIFIER formalParameters optionalBrackets
      (block | ';')
    ;

constructorDeclaration: IDENTIFIER formalParameters block;

fieldDeclaration: typeType variableDeclarators ';';

variableDeclarators: variableDeclarator (',' variableDeclarator)*;

variableDeclarator: variableDeclaratorId ('=' variableInitializer)?;

variableDeclaratorId: IDENTIFIER optionalBrackets;

variableInitializer
    : arrayInitializer
    | expression
    ;

arrayInitializer: '{' (variableInitializer (',' variableInitializer)* (',')? )? '}';

formalParameters: '(' formalParameterList? ')';

formalParameterList: formalParameter (',' formalParameter)*;

formalParameter: FINAL? typeType variableDeclaratorId;

// Statements and blocks
block: '{' blockStatement* '}';

blockStatement
    : FINAL? typeType variableDeclarators ';' #varDeclBlockStatement
    | statement                               #statementBlockStatement
    | typeDeclaration                         #typeDeclBlockStatement
    ;

statement
    : block                                              #newBlockStatement
    | IF '(' expression ')' succBranch=statement (ELSE failBranch=statement)?  #ifStatement
    | WHILE '(' expression ')' statement #whileStatement
    | RETURN? expression? ';'        #expressionStatement
    ;


// Expressions
expressionList: expression (',' expression)*;

methodCall: (IDENTIFIER | THIS) '(' expressionList? ')';

expression
    : primary   #primaryExpression
    | expression bop='.' ( IDENTIFIER | methodCall) #dotExpression
    | ext=expression '[' subscript=expression ']'  #subscriptExpression
    | methodCall #methodCallExpression
    | NEW (qualifiedName | primitiveType) (arrayCreatorRest | classCreatorRest) #newExpression
    | '(' typeType ')' expression #castExpression
    | expression postfix=('++' | '--') #opExpression
    | prefix=('+'|'-'|'++'|'--') expression #opExpression
    | prefix=('~'|'!') expression #opExpression
    | expression bop=('*'|'/'|'%') expression #opExpression
    | expression bop=('+'|'-') expression #opExpression
    | expression bop=('<<' | '>>>' | '>>') expression #opExpression
    | expression bop=('<=' | '>=' | '>' | '<') expression #opExpression
    | expression bop=('==' | '!=') expression #opExpression
    | expression bop='&' expression #opExpression
    | expression bop='^' expression #opExpression
    | expression bop='|' expression #opExpression
    | expression bop='&&' expression #opExpression
    | expression bop='||' expression #opExpression
    | <assoc=right> expression
      bop=('=' | '+=' | '-=' | '*=' | '/=' | '&=' | '|=' | '^=' | '>>=' | '>>>=' | '<<=' | '%=')
      expression  #opExpression
    ;

primary
    : '(' expression ')' #parenthesesPrimary
    | THIS               #thisPrimary
    | literal            #literalPrimary
    | IDENTIFIER         #idPrimary
    ;

arrayCreatorRest
    : ('[' ']')+ arrayInitializer            #initArrayCreatorRest
    | ('[' expression ']')+ optionalBrackets #exprArrayCreatorRest
    ;

classCreatorRest: '(' expressionList? ')' classBody?;

typeType: (qualifiedName | primitiveType) optionalBrackets;

qualifiedName: IDENTIFIER ('.' IDENTIFIER)*;

optionalBrackets: ('[' ']')*;

literal
    : DECIMAL_LITERAL
    | FLOAT_LITERAL
    | CHAR_LITERAL
    | STRING_LITERAL
    | BOOL_LITERAL
    | NULL_LITERAL
    ;

modifier
    : ABSTRACT
    | FINAL
    | PRIVATE
    | PROTECTED
    | PUBLIC
    | STATIC
    ;

primitiveType
    : BOOLEAN
    | BYTE
    | CHAR
    | DOUBLE
    | FLOAT
    | INT
    | LONG
    | SHORT
    ;

// Keywords
ABSTRACT:           'abstract';
FINAL:              'final';
PRIVATE:            'private';
PROTECTED:          'protected';
PUBLIC:             'public';
STATIC:             'static';

BOOLEAN:            'boolean';
BYTE:               'byte';
CHAR:               'char';
CLASS:              'class';
DOUBLE:             'double';
FLOAT:              'float';
INT:                'int';
LONG:               'long';
SHORT:              'short';
VOID:               'void';

ELSE:               'else';
IF:                 'if';
WHILE:              'while';
NEW:                'new';
RETURN:             'return';
THIS:               'this';

// Literals
DECIMAL_LITERAL:    ('0' | [1-9] (Digits? | '_'+ Digits)) [lL]?;
FLOAT_LITERAL:      (Digits '.' Digits? | '.' Digits) [fFdD]?
             |       Digits ([fFdD])
             ;
BOOL_LITERAL:       'true' | 'false';
CHAR_LITERAL:       '\'' (~['\\\r\n]) '\'';
STRING_LITERAL:     '"' (~["\\\r\n])* '"';
NULL_LITERAL:       'null';

// Separators
LPAREN:             '(';
RPAREN:             ')';
LBRACE:             '{';
RBRACE:             '}';
LBRACK:             '[';
RBRACK:             ']';
SEMICOLON:          ';';
COMMA:              ',';
DOT:                '.';

// Operators
ASSIGN:             '=';
GT:                 '>';
LT:                 '<';
BANG:               '!';
TILDE:              '~';
QUESTION:           '?';
COLON:              ':';
EQUAL:              '==';
LE:                 '<=';
GE:                 '>=';
NOTEQUAL:           '!=';
AND:                '&&';
OR:                 '||';
INC:                '++';
DEC:                '--';
ADD:                '+';
SUB:                '-';
MUL:                '*';
DIV:                '/';
BITAND:             '&';
BITOR:              '|';
CARET:              '^';
MOD:                '%';
ADD_ASSIGN:         '+=';
SUB_ASSIGN:         '-=';
MUL_ASSIGN:         '*=';
DIV_ASSIGN:         '/=';
AND_ASSIGN:         '&=';
OR_ASSIGN:          '|=';
XOR_ASSIGN:         '^=';
MOD_ASSIGN:         '%=';
LSHIFT_ASSIGN:      '<<=';
RSHIFT_ASSIGN:      '>>=';
URSHIFT_ASSIGN:     '>>>=';

// Whitespace and comments
WS:                 [ \t\r\n\u000C]+ -> channel(HIDDEN);
COMMENT:            '/*' .*? '*/'    -> channel(HIDDEN);
LINE_COMMENT:       '//' ~[\r\n]*    -> channel(HIDDEN);

// Identifiers
IDENTIFIER:         Letter (Letter | [0-9])*;

// Fragment rules
fragment Digits: [0-9] ([0-9_]* [0-9])?;
fragment Letter
    : [a-zA-Z$_] // these are the "java letters" below 0x7F
    | ~[\u0000-\u007F\uD800-\uDBFF] // covers all characters above 0x7F which are not a surrogate
    | [\uD800-\uDBFF] [\uDC00-\uDFFF] // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
    ;