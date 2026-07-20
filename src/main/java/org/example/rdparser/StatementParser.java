/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.example.rdparser;

import static org.eclipse.jdt.internal.compiler.parser.TerminalToken.*;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;

public class StatementParser {
    
    protected final RDParser parser;
    
    public StatementParser(RDParser parser) {
        this.parser = parser;
    }
    
    public Statement[] parseStatements() {
        List<Statement> statements = new ArrayList<>();
        
        while (this.parser.currentToken != TokenNameEOF && 
               this.parser.currentToken != TokenNameRBRACE) {
            
            Statement stmt = parseStatement();
            if (stmt != null) {
                statements.add(stmt);
            } else if (this.parser.currentToken == TokenNameSEMICOLON) {
                this.parser.consumeToken();
            } else {
                this.parser.synchronize(RecoveryHandler.SYNC_STATEMENT);
            }
        }
        
        return statements.isEmpty() ? null : statements.toArray(new Statement[0]);
    }
    
    public Statement parseStatement() {
        TerminalToken token = this.parser.currentToken;
        
        switch (token) {
            case TokenNameSEMICOLON:
                return parseEmptyStatement();
            case TokenNameLBRACE:
                return parseBlock();
            case TokenNameif:
                return parseIfStatement();
            case TokenNamefor:
                return parseForStatement();
            case TokenNamewhile:
                return parseWhileStatement();
            case TokenNamedo:
                return parseDoStatement();
            case TokenNameswitch:
                return parseSwitchStatement();
            case TokenNametry:
                return parseTryStatement();
            case TokenNamereturn:
                return parseReturnStatement();
            case TokenNamethrow:
                return parseThrowStatement();
            case TokenNamebreak:
                return parseBreakStatement();
            case TokenNamecontinue:
                return parseContinueStatement();
            case TokenNamesynchronized:
                return parseSynchronizedStatement();
            case TokenNameassert:
                return parseAssertStatement();
            default:
                return parseOtherStatement();
        }
    }
    
    protected EmptyStatement parseEmptyStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        EmptyStatement stmt = new EmptyStatement(start, this.parser.endPosition());
        return stmt;
    }
    
    public Block parseBlock() {
        int start = this.parser.startPosition();
        this.parser.match(TokenNameLBRACE);
        
        Block block = new Block(0);
        block.sourceStart = start;
        
        Statement[] statements = parseStatements();
        block.statements = statements;
        
        this.parser.match(TokenNameRBRACE);
        block.sourceEnd = this.parser.endPosition();
        
        return block;
    }
    
    protected IfStatement parseIfStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        this.parser.match(TokenNameLPAREN);
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression condition = exprParser.parseExpression();
        this.parser.match(TokenNameRPAREN);
        
        Statement thenStatement = parseStatement();
        Statement elseStatement = null;
        
        if (this.parser.currentToken == TokenNameelse) {
            this.parser.consumeToken();
            elseStatement = parseStatement();
        }
        
        IfStatement ifStmt = new IfStatement(condition, thenStatement, start, 0);
        if (elseStatement != null) {
            ifStmt.elseStatement = elseStatement;
        }
        ifStmt.sourceEnd = this.parser.endPosition();
        
        return ifStmt;
    }
    
    protected Statement parseForStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        this.parser.match(TokenNameLPAREN);
        
        if (isForEachStart()) {
            return parseForEachStatement(start);
        }
        
        return parseTraditionalForStatement(start);
    }
    
    protected boolean isForEachStart() {
        int currentPos = this.parser.scanner.currentPosition;
        int startPos = this.parser.scanner.startPosition;
        TerminalToken savedToken = this.parser.currentToken;
        
        this.parser.resetModifiers();
        while (this.parser.isModifierToken(this.parser.currentToken) || 
               this.parser.currentToken == TokenNameAT) {
            if (this.parser.currentToken == TokenNameAT) {
                ExpressionParser exprParser = new ExpressionParser(this.parser);
                exprParser.parseAnnotation();
            } else {
                this.parser.consumeToken();
            }
        }
        
        boolean isForEach = false;
        TypeReferenceParser typeParser = new TypeReferenceParser(this.parser);
        typeParser.parseTypeReference();
        
        if (this.parser.isIdentifier()) {
            this.parser.consumeToken();
            if (this.parser.currentToken == TokenNameCOLON) {
                isForEach = true;
            }
        }
        
        this.parser.scanner.currentPosition = currentPos;
        this.parser.scanner.startPosition = startPos;
        this.parser.currentToken = savedToken;
        
        return isForEach;
    }
    
    protected ForStatement parseTraditionalForStatement(int start) {
        Statement[] initializations = null;
        Expression condition = null;
        Statement[] increments = null;
        
        if (this.parser.currentToken != TokenNameSEMICOLON) {
            initializations = parseForInit();
        }
        this.parser.match(TokenNameSEMICOLON);
        
        if (this.parser.currentToken != TokenNameSEMICOLON) {
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            condition = exprParser.parseExpression();
        }
        this.parser.match(TokenNameSEMICOLON);
        
        if (this.parser.currentToken != TokenNameRPAREN) {
            increments = parseForIncrements();
        }
        this.parser.match(TokenNameRPAREN);
        
        Statement action = parseStatement();
        
        ForStatement forStmt = ASTHelper.createForStatement(initializations, condition, increments, action, false, start, this.parser.endPosition());
        
        return forStmt;
    }
    
    protected ForeachStatement parseForEachStatement(int start) {
        this.parser.resetModifiers();
        while (this.parser.isModifierToken(this.parser.currentToken)) {
            this.parser.consumeToken();
        }
        
        TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
        char[] name = this.parser.identifier();
        
        this.parser.match(TokenNameCOLON);
        
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression collection = exprParser.parseExpression();
        
        this.parser.match(TokenNameRPAREN);
        
        Statement action = parseStatement();
        
        LocalDeclaration elementVar = new LocalDeclaration(name, 0, 0);
        elementVar.type = typeRef;
        elementVar.modifiers = this.parser.modifiers;
        
        ForeachStatement foreach = new ForeachStatement(elementVar, start);
        foreach.collection = collection;
        foreach.action = action;
        foreach.sourceEnd = this.parser.endPosition();
        
        return foreach;
    }
    
    protected Statement[] parseForInit() {
        List<Statement> inits = new ArrayList<>();
        
        this.parser.resetModifiers();
        while (this.parser.isModifierToken(this.parser.currentToken)) {
            parseModifier();
        }
        
        TypeReferenceParser typeParser = new TypeReferenceParser(this.parser);
        TypeReference typeRef = typeParser.parseTypeReference();
        
        if (typeRef != null && this.parser.isIdentifier()) {
            LocalDeclaration local = parseLocalDeclaration(typeRef);
            inits.add(local);
            
            while (this.parser.currentToken == TokenNameCOMMA) {
                this.parser.consumeToken();
                local = parseAdditionalLocalDeclaration(local);
                inits.add(local);
            }
        } else {
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            Expression expr = exprParser.parseExpression();
            if (expr != null) {
                inits.add(expr);
            }
            
            while (this.parser.currentToken == TokenNameCOMMA) {
                this.parser.consumeToken();
                expr = exprParser.parseExpression();
                if (expr != null) {
                    inits.add(expr);
                }
            }
        }
        
        return inits.isEmpty() ? null : inits.toArray(new Statement[0]);
    }
    
    protected Statement[] parseForIncrements() {
        List<Statement> increments = new ArrayList<>();
        
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression expr = exprParser.parseExpression();
        if (expr != null) {
            increments.add(expr);
        }
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            expr = exprParser.parseExpression();
            if (expr != null) {
                increments.add(expr);
            }
        }
        
        return increments.isEmpty() ? null : increments.toArray(new Statement[0]);
    }
    
    protected WhileStatement parseWhileStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        this.parser.match(TokenNameLPAREN);
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression condition = exprParser.parseExpression();
        this.parser.match(TokenNameRPAREN);
        
        Statement action = parseStatement();
        
        WhileStatement whileStmt = new WhileStatement(condition, action, start, this.parser.endPosition());
        return whileStmt;
    }
    
    protected Statement parseDoStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        Statement action = parseStatement();
        
        this.parser.match(TokenNamewhile);
        this.parser.match(TokenNameLPAREN);
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression condition = exprParser.parseExpression();
        this.parser.match(TokenNameRPAREN);
        this.parser.match(TokenNameSEMICOLON);
        
        DoStatement doStmt = ASTHelper.createDoStatement(condition, action, start, this.parser.endPosition());
        
        return doStmt;
    }
    
    protected SwitchStatement parseSwitchStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        this.parser.match(TokenNameLPAREN);
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression expression = exprParser.parseExpression();
        this.parser.match(TokenNameRPAREN);
        
        SwitchStatement switchStmt = new SwitchStatement();
        switchStmt.sourceStart = start;
        switchStmt.expression = expression;
        
        this.parser.match(TokenNameLBRACE);
        parseSwitchCases(switchStmt);
        this.parser.match(TokenNameRBRACE);
        
        switchStmt.sourceEnd = this.parser.endPosition();
        
        return switchStmt;
    }
    
    protected void parseSwitchCases(SwitchStatement switchStmt) {
        List<Statement> statements = new ArrayList<>();
        List<CaseStatement> cases = new ArrayList<>();
        
        while (this.parser.currentToken != TokenNameEOF && 
               this.parser.currentToken != TokenNameRBRACE) {
            
            if (this.parser.currentToken == TokenNamecase) {
                CaseStatement caseStmt = parseCaseStatement();
                if (caseStmt != null) {
                    cases.add(caseStmt);
                    statements.add(caseStmt);
                }
            } else if (this.parser.currentToken == TokenNamedefault) {
                CaseStatement defaultCase = parseDefaultCase();
                if (defaultCase != null) {
                    cases.add(defaultCase);
                    statements.add(defaultCase);
                }
            } else {
                Statement stmt = parseStatement();
                if (stmt != null) {
                    statements.add(stmt);
                }
            }
        }
        
        switchStmt.statements = statements.isEmpty() ? null : statements.toArray(new Statement[0]);
    }
    
    protected CaseStatement parseCaseStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        List<Expression> caseExprs = new ArrayList<>();
        Expression caseExpr = exprParser.parseExpression();
        if (caseExpr != null) {
            caseExprs.add(caseExpr);
        }
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            Expression additionalExpr = exprParser.parseExpression();
            if (additionalExpr != null) {
                caseExprs.add(additionalExpr);
            }
        }
        
        if (this.parser.currentToken == TokenNameARROW || 
            this.parser.currentToken == TokenNameCaseArrow) {
            this.parser.consumeToken();
        } else {
            this.parser.match(TokenNameCOLON);
        }
        
        CaseStatement caseStmt = ASTHelper.createCaseStatement(
            caseExprs.isEmpty() ? null : caseExprs.toArray(new Expression[0]), 
            start, this.parser.endPosition());
        return caseStmt;
    }
    
    protected CaseStatement parseDefaultCase() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        if (this.parser.currentToken == TokenNameARROW || 
            this.parser.currentToken == TokenNameCaseArrow) {
            this.parser.consumeToken();
        } else {
            this.parser.match(TokenNameCOLON);
        }
        
        CaseStatement caseStmt = ASTHelper.createCaseStatement(null, start, this.parser.endPosition());
        
        return caseStmt;
    }
    
    protected TryStatement parseTryStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        TryStatement tryStmt = new TryStatement();
        tryStmt.sourceStart = start;
        
        if (this.parser.currentToken == TokenNameLPAREN) {
            tryStmt.resources = parseTryResources();
        }
        
        tryStmt.tryBlock = parseBlock();
        
        while (this.parser.currentToken == TokenNamecatch) {
            CatchClause catchClause = parseCatchClause();
            if (tryStmt.catchBlocks == null) {
                tryStmt.catchBlocks = new Block[1];
                tryStmt.catchArguments = new Argument[1];
                tryStmt.catchBlocks[0] = catchClause.catchBlock;
                tryStmt.catchArguments[0] = catchClause.catchArgument;
            } else {
                int len = tryStmt.catchBlocks.length;
                Block[] newBlocks = new Block[len + 1];
                Argument[] newArgs = new Argument[len + 1];
                System.arraycopy(tryStmt.catchBlocks, 0, newBlocks, 0, len);
                System.arraycopy(tryStmt.catchArguments, 0, newArgs, 0, len);
                newBlocks[len] = catchClause.catchBlock;
                newArgs[len] = catchClause.catchArgument;
                tryStmt.catchBlocks = newBlocks;
                tryStmt.catchArguments = newArgs;
            }
        }
        
        if (this.parser.currentToken == TokenNamefinally) {
            this.parser.consumeToken();
            tryStmt.finallyBlock = parseBlock();
        }
        
        tryStmt.sourceEnd = this.parser.endPosition();
        
        return tryStmt;
    }
    
    protected LocalDeclaration[] parseTryResources() {
        List<LocalDeclaration> resources = new ArrayList<>();
        
        this.parser.match(TokenNameLPAREN);
        
        LocalDeclaration resource = parseResource();
        if (resource != null) {
            resources.add(resource);
        }
        
        while (this.parser.currentToken == TokenNameSEMICOLON) {
            this.parser.consumeToken();
            if (this.parser.currentToken == TokenNameRPAREN) {
                break;
            }
            resource = parseResource();
            if (resource != null) {
                resources.add(resource);
            }
        }
        
        this.parser.match(TokenNameRPAREN);
        
        return resources.isEmpty() ? null : resources.toArray(new LocalDeclaration[0]);
    }
    
    protected LocalDeclaration parseResource() {
        this.parser.resetModifiers();
        while (this.parser.isModifierToken(this.parser.currentToken)) {
            parseModifier();
        }
        
        TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
        if (typeRef == null) {
            return null;
        }
        
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            return null;
        }
        
        char[] name = this.parser.identifier();
        int start = this.parser.startPosition();
        
        LocalDeclaration local = new LocalDeclaration(name, start, this.parser.endPosition());
        local.type = typeRef;
        local.modifiers = this.parser.modifiers;
        
        if (this.parser.currentToken == TokenNameEQUAL) {
            this.parser.consumeToken();
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            local.initialization = exprParser.parseExpression();
        }
        
        return local;
    }
    
    protected CatchClause parseCatchClause() {
        this.parser.consumeToken();
        
        this.parser.match(TokenNameLPAREN);
        
        this.parser.resetModifiers();
        while (this.parser.isModifierToken(this.parser.currentToken)) {
            parseModifier();
        }
        
        TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
        
        List<TypeReference> exceptionTypes = new ArrayList<>();
        exceptionTypes.add(typeRef);
        
        while (this.parser.currentToken == TokenNameOR) {
            this.parser.consumeToken();
            typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
            if (typeRef != null) {
                exceptionTypes.add(typeRef);
            }
        }
        
        char[] name = CharOperation.NO_CHAR;
        if (this.parser.isIdentifier()) {
            name = this.parser.identifier();
        }
        
        Argument catchArg = new Argument(name, 0, null, this.parser.modifiers);
        if (exceptionTypes.size() == 1) {
            catchArg.type = exceptionTypes.get(0);
        } else {
            catchArg.type = new UnionTypeReference(
                exceptionTypes.toArray(new TypeReference[0]));
        }
        
        this.parser.match(TokenNameRPAREN);
        
        Block catchBlock = parseBlock();
        
        CatchClause clause = new CatchClause();
        clause.catchArgument = catchArg;
        clause.catchBlock = catchBlock;
        
        return clause;
    }
    
    protected static class CatchClause {
        Argument catchArgument;
        Block catchBlock;
    }
    
    protected ReturnStatement parseReturnStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        Expression expr = null;
        if (this.parser.currentToken != TokenNameSEMICOLON) {
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            expr = exprParser.parseExpression();
        }
        
        this.parser.match(TokenNameSEMICOLON);
        
        ReturnStatement ret = new ReturnStatement(expr, start, this.parser.endPosition());
        return ret;
    }
    
    protected ThrowStatement parseThrowStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression exception = exprParser.parseExpression();
        
        this.parser.match(TokenNameSEMICOLON);
        
        ThrowStatement throwStmt = new ThrowStatement(exception, start, this.parser.endPosition());
        return throwStmt;
    }
    
    protected BreakStatement parseBreakStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        char[] label = null;
        if (this.parser.isIdentifier()) {
            label = this.parser.identifier();
        }
        
        this.parser.match(TokenNameSEMICOLON);
        
        BreakStatement breakStmt = new BreakStatement(label, start, this.parser.endPosition());
        return breakStmt;
    }
    
    protected ContinueStatement parseContinueStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        char[] label = null;
        if (this.parser.isIdentifier()) {
            label = this.parser.identifier();
        }
        
        this.parser.match(TokenNameSEMICOLON);
        
        ContinueStatement continueStmt = new ContinueStatement(label, start, this.parser.endPosition());
        return continueStmt;
    }
    
    protected SynchronizedStatement parseSynchronizedStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        this.parser.match(TokenNameLPAREN);
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression expression = exprParser.parseExpression();
        this.parser.match(TokenNameRPAREN);
        
        Block block = parseBlock();
        
        SynchronizedStatement syncStmt = new SynchronizedStatement(expression, block, start, this.parser.endPosition());
        return syncStmt;
    }
    
    protected AssertStatement parseAssertStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression assertExpression = exprParser.parseExpression();
        
        Expression message = null;
        if (this.parser.currentToken == TokenNameCOLON) {
            this.parser.consumeToken();
            message = exprParser.parseExpression();
        }
        
        this.parser.match(TokenNameSEMICOLON);
        
        AssertStatement assertStmt;
        if (message != null) {
            assertStmt = new AssertStatement(assertExpression, message, start);
        } else {
            assertStmt = new AssertStatement(assertExpression, start);
        }
        assertStmt.sourceEnd = this.parser.endPosition();
        
        return assertStmt;
    }
    
    protected Statement parseOtherStatement() {
        if (this.parser.isIdentifier()) {
            if (isLabeledStatement()) {
                return parseLabeledStatement();
            }
        }
        
        if (this.parser.currentToken == TokenNameAT ||
            this.parser.isModifierToken(this.parser.currentToken)) {
            return parseLocalDeclaration();
        }
        
        if (this.parser.isTypeStartToken()) {
            Statement stmt = parseLocalDeclaration();
            if (stmt != null) return stmt;
        }
        
        return parseExpressionStatement();
    }
    
    protected boolean isLabeledStatement() {
        int currentPos = this.parser.scanner.currentPosition;
        int startPos = this.parser.scanner.startPosition;
        TerminalToken savedToken = this.parser.currentToken;
        
        this.parser.consumeToken();
        boolean isLabel = this.parser.currentToken == TokenNameCOLON;
        
        this.parser.scanner.currentPosition = currentPos;
        this.parser.scanner.startPosition = startPos;
        this.parser.currentToken = savedToken;
        
        return isLabel;
    }
    
    protected LabeledStatement parseLabeledStatement() {
        int start = this.parser.startPosition();
        char[] label = this.parser.identifier();
        
        this.parser.match(TokenNameCOLON);
        
        Statement statement = parseStatement();
        
        LabeledStatement labeled = new LabeledStatement(label, statement, start, this.parser.endPosition());
        return labeled;
    }
    
    protected LocalDeclaration parseLocalDeclaration() {
        this.parser.resetModifiers();
        while (this.parser.isModifierToken(this.parser.currentToken) || 
               this.parser.currentToken == TokenNameAT) {
            if (this.parser.currentToken == TokenNameAT) {
                ExpressionParser exprParser = new ExpressionParser(this.parser);
                Annotation annotation = exprParser.parseAnnotation();
                if (annotation != null) {
                    this.parser.annotations.add(annotation);
                }
            } else {
                parseModifier();
            }
        }
        
        TerminalToken beforeTypeToken = this.parser.currentToken;
        int beforeStart = this.parser.scanner.startPosition;
        int beforeCurr = this.parser.scanner.currentPosition;
        TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
        if (typeRef != null && !this.parser.isIdentifier() && 
            this.parser.currentToken != TokenNameLBRACKET) {
            this.parser.scanner.currentPosition = beforeCurr;
            this.parser.scanner.startPosition = beforeStart;
            this.parser.currentToken = beforeTypeToken;
            return null;
        }
        if (typeRef == null) {
            System.err.println("DEBUG parseLocalDeclaration: typeRef is null, beforeToken=" + beforeTypeToken
                + " start=" + beforeStart + " curr=" + beforeCurr
                + " afterToken=" + this.parser.currentToken
                + " afterStart=" + this.parser.scanner.startPosition
                + " afterCurr=" + this.parser.scanner.currentPosition);
            return null;
        }
        
        LocalDeclaration local = parseLocalDeclaration(typeRef);
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            parseAdditionalLocalDeclaration(local);
        }
        
        this.parser.match(TokenNameSEMICOLON);
        
        return local;
    }
    
    protected LocalDeclaration parseLocalDeclaration(TypeReference typeRef) {
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            return null;
        }
        
        int start = this.parser.modifiersSourceStart >= 0 ? 
            this.parser.modifiersSourceStart : this.parser.startPosition();
        char[] name = this.parser.identifier();
        
        LocalDeclaration local = new LocalDeclaration(name, start, this.parser.endPosition());
        local.type = typeRef;
        local.modifiers = this.parser.modifiers;
        local.annotations = this.parser.annotations.isEmpty() ? null : 
            this.parser.annotations.toArray(new Annotation[0]);
        
        if (this.parser.currentToken == TokenNameEQUAL) {
            this.parser.consumeToken();
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            local.initialization = exprParser.parseExpression();
        }
        
        local.sourceEnd = this.parser.endPosition();
        local.declarationSourceEnd = local.sourceEnd;
        local.declarationSourceStart = start;
        
        return local;
    }
    
    protected LocalDeclaration parseAdditionalLocalDeclaration(LocalDeclaration first) {
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            return null;
        }
        
        char[] name = this.parser.identifier();
        int start = this.parser.startPosition();
        
        LocalDeclaration local = new LocalDeclaration(name, start, this.parser.endPosition());
        local.type = first.type;
        local.modifiers = first.modifiers;
        
        if (this.parser.currentToken == TokenNameEQUAL) {
            this.parser.consumeToken();
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            local.initialization = exprParser.parseExpression();
        }
        
        local.sourceEnd = this.parser.endPosition();
        local.declarationSourceEnd = local.sourceEnd;
        
        return local;
    }
    
    protected Statement parseExpressionStatement() {
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Expression expr = exprParser.parseExpression();
        
        this.parser.match(TokenNameSEMICOLON);
        return expr;
//        if (expr instanceof Assignment) {
//            return (Statement) expr;
//        }
//        if (expr instanceof MessageSend) {
//            return (Statement) expr;
//        }
//
//        ExpressionStatement stmt = new ExpressionStatement(expr,
//            expr.sourceStart, this.parser.endPosition());
//        return stmt;
    }
    
    protected void parseModifier() {
        TerminalToken token = this.parser.currentToken;
        switch (token) {
            case TokenNamefinal:
                this.parser.checkAndSetModifiers(ClassFileConstants.AccFinal);
                this.parser.consumeToken();
                break;
            case TokenNamestatic:
                this.parser.checkAndSetModifiers(ClassFileConstants.AccStatic);
                this.parser.consumeToken();
                break;
            default:
                this.parser.consumeToken();
                break;
        }
    }
}
