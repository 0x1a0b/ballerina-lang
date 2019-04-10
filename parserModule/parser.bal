import ballerina/io;
import ballerina/log;


OperatorStack oprStack = new();
ExprStack expStack = new();
boolean recovered = true;
//keeps track on error recovery
boolean errorRecovered = true;
//keep track of error tokens
Token[] errTokens = [];
//error token counter
int errCount = 0;

//keep track of next valid occurence
boolean expOperand = true;
//if invalid occurence is captured in an expression
boolean invalidOccurence = false;
//invalid expression
boolean invalidExpression = false;
// tuple list expression count
int tupleListPos = 0;

type Parser object {
	private ParserBufferReader parserBuffer;
	public function __init(ParserBufferReader parserBuffer) {
		self.parserBuffer = parserBuffer;
	}
	public function parse() returns PackageNode {
		DefinitionNode[] dList = [];
		int pos = 0;
		while (!self.parserBuffer.isEOFToken()) {
			Token currToken = self.parserBuffer.consumeToken();
			if (currToken.tokenType == FUNCTION) {
				FunctionNode function1 = self.parseFunction(currToken);
				DefinitionNode defNode = function1;
				if(errorRecovered == false){
					if(errTokens.length()>0){
						Token errTkn  =  errTokens[0];
						ErrorNode fnErNode = {nodeKind: ERROR_NODE,tokenList:[errTkn],errorFunction:function1};
						int fnCount = 0;
						while(fnCount < errTokens.length()){
							fnErNode.tokenList[fnCount] = errTokens[fnCount];
							fnCount += 1;
						}
						dList[pos] = fnErNode;
						pos += 1;
						errorRecovered = true;
					}
					else{
					io:println("recovered false");
						ErrorNode erNode = {nodeKind: ERROR_NODE,errorFunction:function1};
						dList[pos] = erNode;
						pos += 1;
						errorRecovered = true;
					}
				}
				else{
					dList[pos] = defNode;
					pos += 1;
				}
			}
		}
		Token currToken = self.parserBuffer.consumeToken();
		PackageNode pk2 = { nodeKind: PACKAGE_NODE, tokenList: [currToken], definitionList: dList };
		return pk2;
	}

	//method to consume the token from the token buffer
	function matchToken(int mToken , NodeKind rule) returns Token{
		if (self.LAToken(1) == mToken) {
			Token currToken = self.parserBuffer.consumeToken();
			io:println(tokenNames[currToken.tokenType]);
			return currToken;
		} else {
			string capturedErr = tokenNames[self.LAToken(1)];
			Token panicToken = self.panicRecovery(mToken, rule);
			log:printError("Expected " + tokenNames[mToken] + ";found " + capturedErr);
			return panicToken;
		}
	}

	//error recovery token insertion
	function insertToken(int mToken) returns Token {
		return { tokenType: PARSER_ERROR_TOKEN, text: "<missing " + tokenNames[mToken] + ">" , startPos: -1 , endPos:-1,
			lineNumber: 0, index: -1, whiteSpace: "" };
	}
	//error recovery delete token
	function deleteToken() returns Token{
		Token currToken2 = self.parserBuffer.consumeToken();
		log:printError("unexpected Token: " + tokenNames[currToken2.tokenType]);
		//log:printError("Expected " + tokenNames[mToken] + ";found " + tokenNames[currToken2.tokenType]);
		return { tokenType: PARSER_ERROR_TOKEN, text: "<invalid token: " + tokenNames[currToken2.tokenType] + ">" , startPos: currToken2.startPos , endPos:currToken2.endPos,
			lineNumber: currToken2.lineNumber, index: currToken2.index, whiteSpace: "" };

	}
	//panic recovery
	function panicRecovery(int mToken,NodeKind rule) returns Token {
		boolean panicMode = true;
		if(rule == "variableDefinitionStatement"){
			if(mToken == SEMICOLON){
				Token insertSemi = self.insertToken(mToken);
				return insertSemi;
			}
			else{
				//recovered == false;
				recovered = false;
				int[] exprPanic = [SEMICOLON,RBRACE];
				while(panicMode){
					if(self.LAToken(1) == exprPanic[0]){
						panicMode = false;
						break;
					}
					else if (self.LAToken(1) == exprPanic[1]){
						break;
					}
					Token currToken1 = self.parserBuffer.consumeToken();
					errTokens[errCount] = currToken1;
					errCount += 1;
				}
				return { tokenType: PARSER_ERROR_TOKEN, text: "<unexpected Token: " + tokenNames[mToken] + ">" , startPos: -1 , endPos:-1,
					lineNumber: 0, index: -1, whiteSpace: "" };
			}
		}
		else if(rule == "function"){
			errorRecovered = false;
			//check if the expected token is a lBrace, then insert
			if(mToken == RBRACE){
				Token insertRbrace = self.insertToken(mToken);
				return insertRbrace;
			}else if(mToken == LBRACE){
				Token insertLbrace = self.insertToken(mToken);
				return insertLbrace;
			}else{
				int[] functionPanic = [RBRACE,EOF];
				while(panicMode){
					if(self.LAToken(1) == functionPanic[0]){
						panicMode = false;
					}
					else if (self.LAToken(1) == functionPanic[1]){
						return { tokenType: PARSER_ERROR_TOKEN, text: "<unexpected " + tokenNames[self.LAToken(1)] + ">" , startPos: -1 , endPos:-1,
						lineNumber: 0, index: -1, whiteSpace: "" };
					}
					Token currToken1 = self.parserBuffer.consumeToken();
					errTokens[errCount] = currToken1;
					errCount += 1;
				}
				return { tokenType: mToken, text: "<unexpected Token: Expected " + tokenNames[mToken] + ">" , startPos: -1 , endPos:-1,
					lineNumber: 0, index: -1, whiteSpace: "" };
			}
		}
		//this return statement will be in a else statement
		return { tokenType: PARSER_ERROR_TOKEN, text: "<unexpected " + tokenNames[mToken] + ">" , startPos: -1 , endPos:-1,
			lineNumber: 0, index: -1, whiteSpace: "" };

	}
	//lookahead the next token
	function LAToken(int lACount) returns int {
		Token laToken = self.parserBuffer.lookAheadToken(lookAheadCount = lACount);
		int lToken = laToken.tokenType;
		return lToken;
	}

	//get the lookahead token to be returned in errors
	function getLAToken () returns Token{
		Token laToken2 = self.parserBuffer.lookAheadToken(lookAheadCount = 1);
		return laToken2;
	}
	//Function definition
	//    | FUNCTION <callable Unit Signature> <callable Unit body>
	function parseFunction(Token currToken) returns FunctionNode {
		Token functionToken = currToken;
		FunctionSignatureNode? signatureNode = self.parseCallableUnitSignature();
		if(signatureNode == null){
			FunctionNode fn1 = { nodeKind: FUNCTION_NODE, tokenList: [functionToken], fnSignature: null, blockNode:null };
			return fn1;
		}else if (errorRecovered == false){
			FunctionNode fn1 = { nodeKind: FUNCTION_NODE, tokenList: [functionToken], fnSignature: signatureNode, blockNode:null };
			return fn1;
		}else{
			BlockNode bNode = self.parseCallableUnitBody();
			FunctionNode fn1 = { nodeKind: FUNCTION_NODE, tokenList: [functionToken], fnSignature: signatureNode, blockNode:
			bNode };
			return fn1;
		}
	}
	//Callable Unit Signature
	//    | IDENTIFIER () make this a callableUnitSignatureNode
	function parseCallableUnitSignature() returns FunctionSignatureNode? {
		Token identifier = self.matchToken(IDENTIFIER,FUNCTION_NODE);
		if(errorRecovered == false){
		return null;
		}else{
			Token lParen = self.matchToken(LPAREN,FUNCTION_NODE);
			if(errorRecovered == false){
				io:println(lParen.tokenType);
				IdentifierNode idNode = { nodeKind: IDENTIFIER_NODE, tokenList: [identifier], identifier: identifier.text };
				FunctionSignatureNode signature1 = { nodeKind: FN_SIGNATURE_NODE,tokenList: [lParen],functionIdentifier: idNode };
			return signature1;
			}else{
				Token rParen = self.matchToken(RPAREN,FUNCTION_NODE);
				if(errorRecovered == false){
				IdentifierNode idNode = { nodeKind: IDENTIFIER_NODE, tokenList: [identifier], identifier: identifier.text };
				FunctionSignatureNode signature1 = { nodeKind: FN_SIGNATURE_NODE, tokenList: [lParen,rParen],functionIdentifier: idNode };
				return signature1;
				}else{
				IdentifierNode idNode = { nodeKind: IDENTIFIER_NODE, tokenList: [identifier], identifier: identifier.text };
				FunctionSignatureNode signature1 = { nodeKind: FN_SIGNATURE_NODE, tokenList: [lParen, rParen],
				functionIdentifier: idNode };
				return signature1;
				}
			}
			}
	}
	//Callable Unit Body
	//    | { <statement*>} make this blockNode
	function parseCallableUnitBody() returns BlockNode {
		StatementNode[] stsList = [];
		int pos = 0;
		Token lBrace = self.matchToken(LBRACE,FUNCTION_NODE);
		while (self.LAToken(1) != RBRACE) {
			if(self.LAToken(1) == EOF){
			break;
			}
			StatementNode stNode = self.parseStatement();
			if(recovered == false || invalidOccurence == true){
				if(errTokens.length()>0){
					Token errTkn  =  errTokens[0];
					ErrorNode erNode = {nodeKind: ERROR_NODE,tokenList:[errTkn],errorStatement:stNode};
					int listCount = 0;
					while(listCount < errTokens.length()){
						erNode.tokenList[listCount] = errTokens[listCount];
						listCount += 1;
					}
					stsList[pos] = erNode;
					pos += 1;
					recovered = true;
					invalidOccurence = false;
				}
				else{
					ErrorNode erNode = {nodeKind: ERROR_NODE,errorStatement:stNode};
					stsList[pos] = erNode;
					pos += 1;
					recovered = true;
					invalidOccurence = false;
				}
			}else{
				stsList[pos] = stNode;
				pos += 1;
			}
		}
		Token rBrace = self.matchToken(RBRACE,FUNCTION_NODE);
		BlockNode blNode = { nodeKind: BLOCK_NODE, tokenList: [lBrace, rBrace], statementList: stsList };
		return blNode;
	}
	//Statement
	//    |<variable definition statement>
	function parseStatement() returns StatementNode {
		VariableDefinitionStatementNode varD = self.parseVariableDefinitionStatementNode();
		StatementNode stNode = varD;
		return stNode;
	}
	//Variable definition statement
	//    | <valueTypeName> IDENTIFIER SEMICOLON // int a;
	//    | <valeuTypeName>  IDENTIFIER ASSIGN <expression> SEMICOLON // int a  = b + 8;
	function parseVariableDefinitionStatementNode() returns VariableDefinitionStatementNode {
		Token valueTypeTkn = self.parseValueTypeName();
		ValueKind valueKind1 = self.matchValueType(valueTypeTkn);
		Token identifier = self.matchToken(IDENTIFIER,VAR_DEF_STATEMENT_NODE);
		VarRefIdentifier vRef = { nodeKind: VAR_REF_NODE, tokenList: [identifier], varIdentifier: identifier.text };
		if (self.LAToken(1) == SEMICOLON) {
			Token semiC = self.matchToken(SEMICOLON,VAR_DEF_STATEMENT_NODE);
			VariableDefinitionStatementNode vDef = { nodeKind: VAR_DEF_STATEMENT_NODE, tokenList: [valueTypeTkn, semiC],
				valueKind: valueKind1, varIdentifier: vRef, expression: null };
			return vDef;
		} else {
			Token assign = self.matchToken(ASSIGN,VAR_DEF_STATEMENT_NODE);
			ExpressionNode exprNode = self.expressionBuilder();
			//if(exprNode == null){
			//	log:printError("Expected Expression" + ";found null");
			//}
			Token semiC2 = self.matchToken(SEMICOLON,VAR_DEF_STATEMENT_NODE);
			VariableDefinitionStatementNode vDef2 = { nodeKind: VAR_DEF_STATEMENT_NODE, tokenList: [valueTypeTkn,assign, semiC2],
				valueKind: valueKind1, varIdentifier: vRef, expression: exprNode };
			return vDef2;
		}
	}
	function expressionBuilder() returns ExpressionNode {
		tupleListPos = 0;
		boolean isExpr = true;
		while (self.LAToken(1) != SEMICOLON && isExpr == true){

			isExpr = self.parseExpression2();
			//isExpr = self.primaryExpr();
			io:println(isExpr);
			if(isExpr == false){
				recovered = false;
			}
		}
		while (oprStack.peek() != -1) {
			Token operator = oprStack.pop();
			if(operator.tokenType == LPAREN){
				log:printError("<Missing RPAREN>");
				invalidOccurence = true;
				OperatorKind opKind = self.matchOperatorType(operator);
			ExpressionNode expr2 = expStack.pop();
			ExpressionNode expr1 = expStack.pop();
			BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind: opKind,
				leftExpr: expr1, rightExpr: expr2 };
			expStack.push(bExpr);
			}else{
				OperatorKind opKind = self.matchOperatorType(operator);
				ExpressionNode expr2 = expStack.pop();
				ExpressionNode expr1 = expStack.pop();
				BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind: opKind,
					leftExpr: expr1, rightExpr: expr2 };
				expStack.push(bExpr);
			}
		}
		ExpressionNode exp2 = expStack.pop();
		return exp2;
	}
	//primary expression
	//function primaryExpr () returns boolean{
	//boolean secExpr = false;
	//if(self.LAToken(1) == SEMICOLON){
	//invalidExpression = true;
	//}else if(self.LAToken(1) == NUMBER){
	//	Token number = self.matchToken(NUMBER, EXPRESSION_NODE);
	//	IntegerLiteralNode intLit = { nodeKind: INTEGER_LITERAL, tokenList: [number], number: number.text };
	//	SimpleLiteral sLit = intLit;
	//	expStack.push(sLit);
	//	secExpr = self.secondaryExpr();
	//	io:println("secExpr" + secExpr);
	//	if (secExpr == false){
	//	Token invalidToken = self.deleteToken();
	//	errTokens[errCount] = invalidToken;
	//	errCount += 1;
	//	invalidExpression = false;
	//	invalidOccurence = true;
	//	}
	//}else if (self.LAToken(1) == IDENTIFIER){
	//	Token identifier = self.matchToken(IDENTIFIER, EXPRESSION_NODE);
	//	VarRefIdentifier varRef = { nodeKind: VAR_REF_NODE, tokenList: [identifier], varIdentifier: identifier.text };
	//	expStack.push(varRef);
	//	secExpr = self.secondaryExpr();
	//	if (secExpr == false){
	//	Token invalidToken = self.deleteToken();
	//	errTokens[errCount] = invalidToken;
	//	errCount += 1;
	//	invalidExpression = false;
	//	invalidOccurence = true;
	//	}
	//}else if (self.LAToken(1) == LPAREN){
	//	Token lParen = self.matchToken(LPAREN, EXPRESSION_NODE);
	//	oprStack.push(lParen);
	//	boolean er = self.primaryExpr();
	//	io:println("primary" + er);
	//	if(er ==false){
	//	Token invalidToken = self.deleteToken();
	//	errTokens[errCount] = invalidToken;
	//	errCount += 1;
	//	invalidExpression = false;
	//	invalidOccurence = true;
	//	}
	//}//else if(self.LAToken(1) == LBRACE){//record literal
	////	Token lBrace = self.matchToken(LBRACE, EXPRESSION_NODE);
	////	oprStack.push(lBrace);
	////}
	//else{
	//invalidExpression = true;
	//}
	//if(invalidExpression == false){
	//	return true;
	//}
	//return false;
	//}
	////secondary expression
	//function secondaryExpr () returns boolean{
	//if(self.LAToken(1) == SEMICOLON){
	//return true;
	//}else if (self.LAToken(1) == ADD || self.LAToken(1) == SUBSTRACTION || self.LAToken(1) == DIVISION || self.
	//		LAToken(1) == MULTIPLICATION){
	//		while (oprStack.opPrecedence(oprStack.peek()) >= oprStack.opPrecedence(self.LAToken(1))) {
	//				Token operator = oprStack.pop();
	//				OperatorKind opKind = self.matchOperatorType(operator);
	//				ExpressionNode expr2 = expStack.pop();
	//				ExpressionNode expr1 = expStack.pop();
	//				BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind:
	//				opKind,
	//					leftExpr: expr1, rightExpr: expr2 };
	//				expStack.push(bExpr);
	//			}
	//			if (self.LAToken(1) == ADD) {
	//				Token add = self.matchToken(ADD, EXPRESSION_NODE);
	//				oprStack.push(add);
	//			} else if (self.LAToken(1) == MULTIPLICATION){
	//				Token multply = self.matchToken(MULTIPLICATION, EXPRESSION_NODE);
	//				oprStack.push(multply);
	//			} else if (self.LAToken(1) == SUBSTRACTION){
	//				Token subs = self.matchToken(SUBSTRACTION, EXPRESSION_NODE);
	//				oprStack.push(subs);
	//			}
	//			return true;
	//}else if (self.LAToken(1) == RPAREN){
	//	Token rParen = self.matchToken(RPAREN, EXPRESSION_NODE);
	//			while (oprStack.peek() != LPAREN) {
	//				Token operator = oprStack.pop();
	//				if(operator.tokenType == PARSER_ERROR_TOKEN){
	//				log:printError("<missing token>");
	//				invalidOccurence = true;
	//				break;
	//				}
	//				OperatorKind opKind = self.matchOperatorType(operator);
	//				ExpressionNode expr2 = expStack.pop();
	//				ExpressionNode expr1 = expStack.pop();
	//				BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind:
	//				opKind,leftExpr: expr1, rightExpr: expr2 };
	//				expStack.push(bExpr);
	//			}
	//			//popping the lParen
	//			Token leftToken = oprStack.pop();
	//
	//			ExpressionNode parenExpr = expStack.topExpr();
	//			if (parenExpr is BinaryExpressionNode) {
	//				Token finalOperator = parenExpr.tokenList[0];
	//				parenExpr.tokenList = [finalOperator, rParen, leftToken];
	//			}
	//			return true;
	//}
	//return false;
	//}

	//Expression
	//    | <simple literal>
	//    | <varaiable reference>
	//    | <recod literal>
	//    | expression ( ADD | SUB ) expression
	//    | expression (DIVISION | MULTIPLICATION) expression
	//expression is parsed using shunting yard algorithm
	function parseExpression2() returns boolean {
		if (self.LAToken(1) == LPAREN) {
			if (expOperand == false) {
				Token invalidToken = self.deleteToken();
				errTokens[errCount] = invalidToken;
				errCount += 1;
				invalidOccurence = true;
				return true;
			} else {
				Token lParen = self.matchToken(LPAREN, EXPRESSION_NODE);
				//check for empty tuple literal
				if(self.LAToken(1) == RPAREN){
					Token rParen = self.matchToken(RPAREN, EXPRESSION_NODE);
					EmptyTupleLiteralNode emptyTuple = {nodeKind:EMPTY_TUPLE_LITERAL_NODE, tokenList:[lParen,rParen] };
					SimpleLiteral smLiteral = emptyTuple;
					expStack.push(smLiteral);
					return true;
				}else{
					oprStack.push(lParen);
					expOperand = true;
					return true;
				}

			}
		} else if (self.LAToken(1) == NUMBER){
			if (expOperand == false) {
				Token invalidToken = self.deleteToken();
				errTokens[errCount] = invalidToken;
				errCount += 1;
				invalidOccurence = true;
				return true;
			} else {
				Token number = self.matchToken(NUMBER, EXPRESSION_NODE);
				IntegerLiteralNode intLit = { nodeKind: INTEGER_LITERAL, tokenList: [number], number: number.text };
				SimpleLiteral sLit = intLit;
				expStack.push(sLit);
				expOperand = false;
				return true;
			}
		} else if (self.LAToken(1) == IDENTIFIER){
			if (expOperand == false) {
				Token invalidToken = self.deleteToken();
				errTokens[errCount] = invalidToken;
				errCount += 1;
				invalidOccurence = true;
				return true;
			} else {
				Token identifier = self.matchToken(IDENTIFIER, EXPRESSION_NODE);
				VarRefIdentifier varRef = { nodeKind: VAR_REF_NODE, tokenList: [identifier], varIdentifier: identifier.
				text };
				expStack.push(varRef);
				expOperand = false;
				return true;
			}
		} else if (self.LAToken(1) == ADD || self.LAToken(1) == SUBSTRACTION || self.LAToken(1) == DIVISION || self.
			LAToken(1) == MULTIPLICATION ||self.LAToken(1) == COMMA ) {
			if (expOperand == true) {
				Token invalidToken = self.deleteToken();
				errTokens[errCount] = invalidToken;
				errCount += 1;
				invalidOccurence = true;
				return true;
			} else {
				while (oprStack.opPrecedence(oprStack.peek()) >= oprStack.opPrecedence(self.LAToken(1))) {
					Token operator = oprStack.pop();
					OperatorKind opKind = self.matchOperatorType(operator);
					ExpressionNode expr2 = expStack.pop();
					ExpressionNode expr1 = expStack.pop();

					BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind:
					opKind,
						leftExpr: expr1, rightExpr: expr2 };
					expStack.push(bExpr);
				}
				if (self.LAToken(1) == ADD) {
					Token add = self.matchToken(ADD, EXPRESSION_NODE);
					oprStack.push(add);
				} else if (self.LAToken(1) == MULTIPLICATION){
					Token multply = self.matchToken(MULTIPLICATION, EXPRESSION_NODE);
					oprStack.push(multply);
				} else if (self.LAToken(1) == SUBSTRACTION){
					Token subs = self.matchToken(SUBSTRACTION, EXPRESSION_NODE);
					oprStack.push(subs);
				}else if (self.LAToken (1) == COMMA){
					Token comma1 = self.matchToken(COMMA, EXPRESSION_NODE);
					oprStack.push(comma1);
				}
				expOperand = true;
				return true;
			}
		} else if (self.LAToken(1) == RPAREN){
			//tuple expression list
			ExpressionNode[] tupleList = [];
			if (expOperand == true) {
				Token invalidToken = self.deleteToken();
				errTokens[errCount] = invalidToken;
				errCount += 1;
				invalidOccurence = true;
				return true;
			} else {
				Token rParen = self.matchToken(RPAREN, EXPRESSION_NODE);
				while (oprStack.peek() != LPAREN) {
					Token operator = oprStack.pop();
					if(operator.tokenType == PARSER_ERROR_TOKEN){
					log:printError("<missing token>");
					invalidOccurence = true;
					break;
					}
					OperatorKind opKind = self.matchOperatorType(operator);
					ExpressionNode expr2 = expStack.pop();
					ExpressionNode expr1 = expStack.pop();
					BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind:
					opKind,leftExpr: expr1, rightExpr: expr2 };
					expStack.push(bExpr);
				}
				//popping the lParen
				Token leftToken = oprStack.pop();

				//ExpressionNode parenExpr = expStack.topExpr();
				ExpressionNode parenExpr = expStack.pop();
				//if (parenExpr is BinaryExpressionNode) {
					//Token finalOperator = parenExpr.tokenList[0];
					//parenExpr.tokenList = [finalOperator, rParen, leftToken];
					tupleList[tupleListPos] = parenExpr;
					tupleListPos += 1;
					TupleLiteralNode tupleLNode = {nodeKind: TUPLE_LITERAL_NODE , tokenList: [rParen , leftToken ],tupleExprList:tupleList };
					expStack.push(tupleLNode);

				//}
				expOperand = false;
				return true;
			}

		}else if (self.LAToken(1) == LBRACE){//for record literal
			RecordKeyValueNode[] recordList = [];
			int pos = 0;
			if (expOperand == false) {
				Token invalidToken = self.deleteToken();
				errTokens[errCount] = invalidToken;
				errCount += 1;
				invalidOccurence = true;
				return true;
			} else {
				Token lBrace = self.matchToken(LBRACE, EXPRESSION_NODE);
				oprStack.push(lBrace);
				if(self.LAToken(1) != RBRACE){
				//expOperand = true;
				RecordKeyValueNode rkNode = self.parseRecordKeyValue();
				if(self.LAToken(1) == RBRACE){
				Token rBrace1 = self.matchToken(RBRACE, EXPRESSION_NODE);
				//pop the lBrace
				Token leftBrace2 = oprStack.pop();
				recordList[pos] = rkNode;
				pos += 1;
				RecordLiteralNode rLiteralNode = {nodeKind:RECORD_LITERAL_NODE,tokenList:[leftBrace2,rBrace1],recordkeyValueList:recordList};
				expStack.push(rLiteralNode);
				return true;
				}else{
				recordList[pos] = rkNode;
				pos += 1;
					while(self.LAToken(1) != RBRACE){
						if(self.LAToken(1) == COMMA){
							expOperand = true;
							Token comma = self.matchToken(COMMA, EXPRESSION_NODE);
						}
					RecordKeyValueNode rkNode2 = self.parseRecordKeyValue();
					recordList[pos] = rkNode2;
					pos += 1;
					}
					//while (oprStack.peek() != LBRACE) {
					//
					//	//ExpressionNode expr2 = expStack.pop();
					//	//ExpressionNode expr1 = expStack.pop();
					//	//BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind:
					//	//opKind,leftExpr: expr1, rightExpr: expr2 };
					//	//expStack.push(bExpr);
					//}
					//pop the LBrace
					Token leftToken23 = oprStack.pop();
					Token rBrace1 = self.matchToken(RBRACE, EXPRESSION_NODE);
					RecordLiteralNode rLiteralNode = {nodeKind:RECORD_LITERAL_NODE,tokenList:[lBrace,rBrace1],recordkeyValueList:recordList};
					expStack.push(rLiteralNode);

				}
				}else if (self.LAToken(1) == RBRACE){
				Token leftBrace = oprStack.pop();
				Token rBrace = self.matchToken(RBRACE, EXPRESSION_NODE);
				RecordLiteralNode recordNode = {nodeKind:RECORD_LITERAL_NODE, tokenList:[leftBrace,rBrace],recordkeyValueList:recordList};
				expStack.push(recordNode);
				return true;
				}
				}
				return true;
			}
			return false;
		}

	//record key value
	//recordKey COLON expression
	function parseRecordKeyValue() returns RecordKeyValueNode{
	//record key
		//boolean sf = self.parseRecordKey();
		if(self.LAToken(1) == IDENTIFIER && self.LAToken(2) == COLON){
			Token id = self.matchToken(IDENTIFIER, EXPRESSION_NODE);
			IdentifierNode idNode = {nodeKind:IDENTIFIER_NODE,tokenList:[id],identifier:id.text};
			RecordKeyNode idkeyNode = {nodeKind:RECORD_KEY_NODE,recordKey: idNode};
			Token colon = self.matchToken(COLON, EXPRESSION_NODE);
			OperatorKind opKind = self.matchOperatorType(colon);
			expOperand = true;

			boolean sf2 = true;
			while(self.LAToken(1) != RBRACE && self.LAToken(1) != COMMA && sf2 == true){
				sf2 = self.parseExpression2();
				if(sf2 == false){
				recovered = false;
				}
			}
			while (oprStack.peek() != LBRACE) {
				Token operator = oprStack.pop();
				OperatorKind opKind1 = self.matchOperatorType(operator);
				ExpressionNode expr2 = expStack.pop();
				ExpressionNode expr1 = expStack.pop();
				BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind:
				opKind1,leftExpr: expr1, rightExpr: expr2 };
				expStack.push(bExpr);
			}
			ExpressionNode expr4 = expStack.pop();
			RecordKeyValueNode rKeyValueNode = {nodeKind:RECORD_KEY_VALUE_NODE, tokenList:[colon],recordKeyNode:idkeyNode,operatorKind:opKind,recordValueExpression:expr4};
			return rKeyValueNode;

		}else{
			boolean parseRKey = true;
			while(self.LAToken(1) != COLON && parseRKey == true ){
				parseRKey = self.parseExpression2();
			}
			while (oprStack.peek() != LBRACE) {
				Token operator = oprStack.pop();
				OperatorKind opKind1 = self.matchOperatorType(operator);
				ExpressionNode expr2 = expStack.pop();
				ExpressionNode expr1 = expStack.pop();
				BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind:
				opKind1,leftExpr: expr1, rightExpr: expr2 };
				expStack.push(bExpr);
			}
		 	ExpressionNode expr4 = expStack.pop();
		 	RecordKeyNode idkeyNode = {nodeKind:RECORD_KEY_NODE,recordExpression: expr4};
		 	Token colon = self.matchToken(COLON, EXPRESSION_NODE);
		 	OperatorKind opKind = self.matchOperatorType(colon);
			expOperand = true;

			boolean sf3 = true;
			while(self.LAToken(1) != RBRACE && self.LAToken(1) != COMMA && sf3 == true){
				sf3 = self.parseExpression2();
				if(sf3 == false){
				recovered = false;
				}
			}
			while (oprStack.peek() != LBRACE) {
				Token operator = oprStack.pop();
				OperatorKind opKind1 = self.matchOperatorType(operator);
				ExpressionNode expr2 = expStack.pop();
				ExpressionNode expr1 = expStack.pop();
				BinaryExpressionNode bExpr = { nodeKind: BINARY_EXP_NODE, tokenList: [operator], operatorKind:
				opKind1,leftExpr: expr1, rightExpr: expr2 };
				expStack.push(bExpr);
			}
			ExpressionNode expr5 = expStack.pop();
			//RecordKeyNode idkeyNode2 = {nodeKind:RECORD_KEY_NODE,recordExpression: expr5};
			RecordKeyValueNode rKeyValueNode = {nodeKind:RECORD_KEY_VALUE_NODE, tokenList:[colon],recordKeyNode:idkeyNode,operatorKind:opKind,recordValueExpression:expr5};
			return rKeyValueNode;
		 }
	}
	//function parseRecordKey() returns boolean{
	//	if(self.LAToken(1) == IDENTIFIER){
	//		Token id = self.matchToken(IDENTIFIER, EXPRESSION_NODE);
	//		IdentifierNode idNode = {nodeKind:IDENTIFIER_NODE,identifier: id.text};
	//	}else{
	//	 boolean parseRKey = self.parseExpression2();
	//	 return parseRKey;
	//	 }
	//}
	//valueTypeName
	//    | INT
	//    | STRING
	function parseValueTypeName() returns Token {
		if (self.LAToken(1) == INT) {
			Token int1 = self.matchToken(INT,STATEMENT_NODE);
			return int1;
		} else {
			Token string1 = self.matchToken(STRING,STATEMENT_NODE);
			return string1;
		}
	}

	//check the value type for the given token
	function matchValueType(Token valueTypeTkn) returns ValueKind {
		if (tokenNames[valueTypeTkn.tokenType] == "INT") {
			return INT_TYPE;
		} else {
			return STRING_TYPE;
		}
	}
	//check the operator kind of the operator token
	function matchOperatorType(Token operator) returns OperatorKind {
		if (tokenNames[operator.tokenType] == "ADD") {
			return PLUS_OP;
		} else if (tokenNames[operator.tokenType] == "SUBSTRACTION"){
			return MINUS_OP;
		} else if (tokenNames[operator.tokenType] == "DIVISION"){
			return DIVISION_OP;
		} else if (tokenNames[operator.tokenType] == "MULTIPLICATION") {
			return MULTIPLICATION_OP;
		}else if(tokenNames[operator.tokenType] == "COLON"){
			return COLON_OP;
		}else if(tokenNames[operator.tokenType] == "COMMA"){
			return COMMA_OP;
		}else{
			return ERROR_OP;
		}
	}
};
//operator stack to store the operators
type OperatorStack object {
	Token[] opStack = [];
	int top;
	public function __init(int top = 0) {
		self.top = top;
	}
	function push(Token token2) {
		self.opStack[self.top] = token2;
		self.top = self.top + 1;
	}
	function pop() returns Token {
		if(self.top != 0){
			self.top = self.top - 1;
			Token operatorToken = self.opStack[self.top];
			return operatorToken;
		}else{
		return { tokenType: PARSER_ERROR_TOKEN, text: "<missing operator>" , startPos: -1 , endPos:-1,
			lineNumber: 0, index: -1, whiteSpace: "" };
		}
	}
	function peek() returns int {
		int topTkn2 = -1;
		if (self.top != 0) {
			Token topTkn = self.opStack[self.top - 1];
			topTkn2 = topTkn.tokenType;
		}
		return topTkn2;
	}
	//ToDo: add the operatory associativity
	function opPrecedence(int opToken) returns int {
		if(opToken == COMMA){
			return 0;
		}else if (opToken == ADD || opToken == SUBSTRACTION) {
			return 1;
		} else if (opToken == DIVISION || opToken == MULTIPLICATION){
			return 2;
		}else if (opToken == COLON){
			return 3;
		}
		return -1;
	}
};

type ExprStack object {
	ExpressionNode[] exprStack = [];
	int top;
	public function __init(int top = 0) {
		self.top = top;
	}
	function push(ExpressionNode token2) {
		self.exprStack[self.top] = token2;
		self.top = self.top + 1;
	}
	function pop() returns ExpressionNode {
		if(self.top != 0) {
			self.top = self.top - 1;
			ExpressionNode exprsNode = self.exprStack[self.top];
			return exprsNode;
		}
	}
	function topExpr() returns ExpressionNode {
		ExpressionNode topExpr = self.exprStack[self.top -1];
		return topExpr;
	}
};

//Grammar
//Function definition
//    | FUNCTION <callable Unit Signature> <callable Unit body>
//Callable Unit Signature
//    | IDENTIFIER ()
//Callable Unit Body
//    | { <statement*>}
//Statement
//    |<variable definition statement>
//
//Variable definition statement
//    | <valueTypeName> IDENTIFIER SEMICOLON // int a;
//    | <valeuTypeName>  IDENTIFIER ASSIGN <expression> SEMICOLON // int a  = b;
//
//valueTypeName
//    | INT
//    | STRING
//Expression
//    | <simple literal>
//    | <varaiable reference>
//    | expression ( ADD | SUB ) expression
//    | expression (DIVISION | MULTIPLICATION) expression
//
//Simple Literal
//    | IntegerLiteral //[0-9]
//
//Variable reference
//    | IDENTIFIER
