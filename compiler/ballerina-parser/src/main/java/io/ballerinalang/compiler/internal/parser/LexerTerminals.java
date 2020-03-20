/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerinalang.compiler.internal.parser;

/**
 * Contains lexer terminal nodes. Includes keywords, syntaxes, and operators.
 * 
 * @since 1.2.0
 */
public class LexerTerminals {

    // Keywords
    public static final String PUBLIC = "public";
    public static final String PRIVATE = "private";
    public static final String FUNCTION = "function";
    public static final String RETURN = "return";
    public static final String RETURNS = "returns";
    public static final String EXTERNAL = "external";
    public static final String TYPE = "type";
    public static final String RECORD = "record";
    public static final String OBJECT = "object";
    public static final String REMOTE = "remote";
    public static final String ABSTRACT = "abstract";
    public static final String CLIENT = "client";

    // Types
    public static final String INT = "int";
    public static final String FLOAT = "float";
    public static final String STRING = "string";
    public static final String BOOLEAN = "boolean";

    // Separators
    public static final char SEMICOLON = ';';
    public static final char COLON = ':';
    public static final char DOT = '.';
    public static final char COMMA = ',';
    public static final char OPEN_PARANTHESIS = '(';
    public static final char CLOSE_PARANTHESIS = ')';
    public static final char OPEN_BRACE = '{';
    public static final char CLOSE_BRACE = '}';
    public static final char OPEN_BRACKET = '[';
    public static final char CLOSE_BRACKET = ']';
    public static final char PIPE = '|';
    public static final char QUESTION_MARK = '?';

    // Arithmetic operators
    public static final char EQUAL = '=';
    public static final char ADD = '+';
    public static final char SUB = '-';
    public static final char MUL = '*';
    public static final char DIV = '/';
    public static final char MOD = '%';
    public static final char GT = '>';
    public static final char LT = '<';

    // Other
    public static final char NEWLINE = '\n'; // equivalent to 0xA
}
