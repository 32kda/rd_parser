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
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;

public class ExpressionParser {
    
    protected final RDParser parser;
    
    public ExpressionParser(RDParser parser) {
        this.parser = parser;
    }
    
    public Expression parseExpression() {
        return parseAssignmentExpression();
    }
    
    protected Expression parseAssignmentExpression() {
        Expression left = parseConditionalExpression();
        
        TerminalToken token = this.parser.currentToken;
        if (isAssignmentOperator(token)) {
            int op = getAssignmentOperator(token);
            this.parser.consumeToken();
            
            Expression right = parseAssignmentExpression();
            
            Assignment assignment = new Assignment(left, right, left.sourceStart);
            assignment.sourceEnd = this.parser.endPosition();
            
            if (op != 0) {
                return new CompoundAssignment(left, right, op, left.sourceEnd);
            }
            
            return assignment;
        }
        
        return left;
    }
    
    protected boolean isAssignmentOperator(TerminalToken token) {
        return token == TokenNameEQUAL ||
               token == TokenNamePLUS_EQUAL ||
               token == TokenNameMINUS_EQUAL ||
               token == TokenNameMULTIPLY_EQUAL ||
               token == TokenNameDIVIDE_EQUAL ||
               token == TokenNameREMAINDER_EQUAL ||
               token == TokenNameAND_EQUAL ||
               token == TokenNameOR_EQUAL ||
               token == TokenNameXOR_EQUAL ||
               token == TokenNameLEFT_SHIFT_EQUAL ||
               token == TokenNameRIGHT_SHIFT_EQUAL ||
               token == TokenNameUNSIGNED_RIGHT_SHIFT_EQUAL;
    }
    
    protected int getAssignmentOperator(TerminalToken token) {
        if (token == TokenNamePLUS_EQUAL) return OperatorIds.PLUS;
        if (token == TokenNameMINUS_EQUAL) return OperatorIds.MINUS;
        if (token == TokenNameMULTIPLY_EQUAL) return OperatorIds.MULTIPLY;
        if (token == TokenNameDIVIDE_EQUAL) return OperatorIds.DIVIDE;
        if (token == TokenNameREMAINDER_EQUAL) return OperatorIds.REMAINDER;
        if (token == TokenNameAND_EQUAL) return OperatorIds.AND;
        if (token == TokenNameOR_EQUAL) return OperatorIds.OR;
        if (token == TokenNameXOR_EQUAL) return OperatorIds.XOR;
        if (token == TokenNameLEFT_SHIFT_EQUAL) return OperatorIds.LEFT_SHIFT;
        if (token == TokenNameRIGHT_SHIFT_EQUAL) return OperatorIds.RIGHT_SHIFT;
        if (token == TokenNameUNSIGNED_RIGHT_SHIFT_EQUAL) return OperatorIds.UNSIGNED_RIGHT_SHIFT;
        return 0;
    }
    
    protected Expression parseConditionalExpression() {
        Expression expr = parseConditionalOrExpression();
        
        if (this.parser.currentToken == TokenNameQUESTION) {
            this.parser.consumeToken();
            
            Expression trueExpr = parseExpression();
            this.parser.match(TokenNameCOLON);
            Expression falseExpr = parseConditionalExpression();
            
            ConditionalExpression cond = new ConditionalExpression(expr, trueExpr, falseExpr);
            cond.sourceStart = expr.sourceStart;
            cond.sourceEnd = this.parser.endPosition();
            
            return cond;
        }
        
        return expr;
    }
    
    protected Expression parseConditionalOrExpression() {
        Expression left = parseConditionalAndExpression();
        
        while (this.parser.currentToken == TokenNameOR_OR) {
            this.parser.consumeToken();
            Expression right = parseConditionalAndExpression();
            
            left = new BinaryExpression(left, right, OperatorIds.OR_OR);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected Expression parseConditionalAndExpression() {
        Expression left = parseInclusiveOrExpression();
        
        while (this.parser.currentToken == TokenNameAND_AND) {
            this.parser.consumeToken();
            Expression right = parseInclusiveOrExpression();
            
            left = new BinaryExpression(left, right, OperatorIds.AND_AND);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected Expression parseInclusiveOrExpression() {
        Expression left = parseExclusiveOrExpression();
        
        while (this.parser.currentToken == TokenNameOR) {
            this.parser.consumeToken();
            Expression right = parseExclusiveOrExpression();
            
            left = new BinaryExpression(left, right, OperatorIds.OR);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected Expression parseExclusiveOrExpression() {
        Expression left = parseAndExpression();
        
        while (this.parser.currentToken == TokenNameXOR) {
            this.parser.consumeToken();
            Expression right = parseAndExpression();
            
            left = new BinaryExpression(left, right, OperatorIds.XOR);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected Expression parseAndExpression() {
        Expression left = parseEqualityExpression();
        
        while (this.parser.currentToken == TokenNameAND) {
            this.parser.consumeToken();
            Expression right = parseEqualityExpression();
            
            left = new BinaryExpression(left, right, OperatorIds.AND);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected Expression parseEqualityExpression() {
        Expression left = parseRelationalExpression();
        
        while (this.parser.currentToken == TokenNameEQUAL_EQUAL || 
               this.parser.currentToken == TokenNameNOT_EQUAL) {
            int op = this.parser.currentToken == TokenNameEQUAL_EQUAL ? 
                OperatorIds.EQUAL_EQUAL : OperatorIds.NOT_EQUAL;
            this.parser.consumeToken();
            Expression right = parseRelationalExpression();
            
            left = new BinaryExpression(left, right, op);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected Expression parseRelationalExpression() {
        Expression left = parseShiftExpression();
        
        if (this.parser.currentToken == TokenNameinstanceof) {
            this.parser.consumeToken();
            TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
            
            InstanceOfExpression instanceOf = new InstanceOfExpression(left, typeRef);
            instanceOf.sourceStart = left.sourceStart;
            instanceOf.sourceEnd = this.parser.endPosition();
            
            if (this.parser.isIdentifier()) {
                char[] name = this.parser.identifier();
                LocalDeclaration patternVar = new LocalDeclaration(name, 0, 0);
                patternVar.type = typeRef;
                instanceOf.pattern = new TypePattern(patternVar);
                instanceOf.sourceEnd = this.parser.endPosition();
            }
            
            return instanceOf;
        }
        
        while (isRelationalOperator(this.parser.currentToken)) {
            int op = getRelationalOperator(this.parser.currentToken);
            this.parser.consumeToken();
            Expression right = parseShiftExpression();
            
            left = new BinaryExpression(left, right, op);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected boolean isRelationalOperator(TerminalToken token) {
        return token == TokenNameLESS ||
               token == TokenNameGREATER ||
               token == TokenNameLESS_EQUAL ||
               token == TokenNameGREATER_EQUAL;
    }
    
    protected int getRelationalOperator(TerminalToken token) {
        if (token == TokenNameLESS) return OperatorIds.LESS;
        if (token == TokenNameGREATER) return OperatorIds.GREATER;
        if (token == TokenNameLESS_EQUAL) return OperatorIds.LESS_EQUAL;
        if (token == TokenNameGREATER_EQUAL) return OperatorIds.GREATER_EQUAL;
        return 0;
    }
    
    protected Expression parseShiftExpression() {
        Expression left = parseAdditiveExpression();
        
        while (isShiftOperator(this.parser.currentToken)) {
            int op = getShiftOperator(this.parser.currentToken);
            this.parser.consumeToken();
            Expression right = parseAdditiveExpression();
            
            left = new BinaryExpression(left, right, op);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected boolean isShiftOperator(TerminalToken token) {
        return token == TokenNameLEFT_SHIFT ||
               token == TokenNameRIGHT_SHIFT ||
               token == TokenNameUNSIGNED_RIGHT_SHIFT;
    }
    
    protected int getShiftOperator(TerminalToken token) {
        if (token == TokenNameLEFT_SHIFT) return OperatorIds.LEFT_SHIFT;
        if (token == TokenNameRIGHT_SHIFT) return OperatorIds.RIGHT_SHIFT;
        if (token == TokenNameUNSIGNED_RIGHT_SHIFT) return OperatorIds.UNSIGNED_RIGHT_SHIFT;
        return 0;
    }
    
    protected Expression parseAdditiveExpression() {
        Expression left = parseMultiplicativeExpression();
        
        while (this.parser.currentToken == TokenNamePLUS || 
               this.parser.currentToken == TokenNameMINUS) {
            int op = this.parser.currentToken == TokenNamePLUS ? 
                OperatorIds.PLUS : OperatorIds.MINUS;
            this.parser.consumeToken();
            Expression right = parseMultiplicativeExpression();
            
            left = new BinaryExpression(left, right, op);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected Expression parseMultiplicativeExpression() {
        Expression left = parseUnaryExpression();
        
        while (this.parser.currentToken == TokenNameMULTIPLY || 
               this.parser.currentToken == TokenNameDIVIDE ||
               this.parser.currentToken == TokenNameREMAINDER) {
            int op = getMultiplicativeOperator(this.parser.currentToken);
            this.parser.consumeToken();
            Expression right = parseUnaryExpression();
            
            left = new BinaryExpression(left, right, op);
            left.sourceEnd = this.parser.endPosition();
        }
        
        return left;
    }
    
    protected int getMultiplicativeOperator(TerminalToken token) {
        if (token == TokenNameMULTIPLY) return OperatorIds.MULTIPLY;
        if (token == TokenNameDIVIDE) return OperatorIds.DIVIDE;
        if (token == TokenNameREMAINDER) return OperatorIds.REMAINDER;
        return 0;
    }
    
    protected Expression parseUnaryExpression() {
        TerminalToken token = this.parser.currentToken;
        
        if (token == TokenNamePLUS) {
            this.parser.consumeToken();
            Expression expr = parseUnaryExpression();
            return new UnaryExpression(expr, OperatorIds.PLUS);
        }
        
        if (token == TokenNameMINUS) {
            this.parser.consumeToken();
            Expression expr = parseUnaryExpression();
            return new UnaryExpression(expr, OperatorIds.MINUS);
        }
        
        return parsePreIncrementExpression();
    }
    
    protected Expression parsePreIncrementExpression() {
        TerminalToken token = this.parser.currentToken;
        int start = this.parser.startPosition();
        if (token == TokenNamePLUS_PLUS) {
            this.parser.consumeToken();
            Expression expr = parseUnaryExpression();
            return new PrefixExpression(expr, expr, OperatorIds.PLUS, start);
        }
        
        if (token == TokenNameMINUS_MINUS) {
            this.parser.consumeToken();
            Expression expr = parseUnaryExpression();
            return new PrefixExpression(expr, expr, OperatorIds.MINUS, start);
        }
        
        if (token == TokenNameNOT) {
            this.parser.consumeToken();
            Expression expr = parseUnaryExpression();
            return new UnaryExpression(expr, OperatorIds.NOT);
        }
        
        if (token == TokenNameTWIDDLE) {
            this.parser.consumeToken();
            Expression expr = parseUnaryExpression();
            return new UnaryExpression(expr, OperatorIds.TWIDDLE);
        }
        
        return parsePostfixExpression();
    }
    
    protected Expression parsePostfixExpression() {
        Expression expr = parsePrimaryExpression();
        
        while (this.parser.currentToken == TokenNamePLUS_PLUS || 
               this.parser.currentToken == TokenNameMINUS_MINUS) {
            int op = this.parser.currentToken == TokenNamePLUS_PLUS ? 
                OperatorIds.PLUS : OperatorIds.MINUS;
            this.parser.consumeToken();
            expr = new PostfixExpression(expr, null, op, this.parser.endPosition());
        }
        
        return expr;
    }
    
    protected Expression parsePrimaryExpression() {
        Expression expr = parsePrimaryExpressionNoSuffix();
        
        while (true) {
            if (this.parser.currentToken == TokenNameCOLON_COLON) {
                expr = parseReferenceExpression(expr);
            } else if (this.parser.currentToken == TokenNameDOT) {
                expr = parseFieldAccess(expr);
            } else if (this.parser.currentToken == TokenNameLBRACKET) {
                int savedPos = this.parser.scanner.currentPosition;
                int savedStart = this.parser.scanner.startPosition;
                TerminalToken savedToken = this.parser.currentToken;
                this.parser.consumeToken();
                boolean isEmptyArray = this.parser.currentToken == TokenNameRBRACKET;
                this.parser.scanner.currentPosition = savedPos;
                this.parser.scanner.startPosition = savedStart;
                this.parser.currentToken = savedToken;
                if (isEmptyArray) {
                    this.parser.consumeToken();
                    this.parser.match(TokenNameRBRACKET);
                } else {
                    expr = parseArrayAccess(expr);
                }
            } else if (this.parser.currentToken == TokenNameLPAREN) {
                expr = parseMethodInvocation(expr);
            } else if (this.parser.currentToken == TokenNameARROW) {
                expr = parseLambdaExpression(expr);
            } else {
                break;
            }
        }
        
        return expr;
    }
    
    protected Expression parseReferenceExpression(Expression lhs) {
        this.parser.consumeToken();
        
        ReferenceExpression refExpr = ASTHelper.createReferenceExpression(this.parser.scanner);
        
        int start = lhs.sourceStart;
        int nameStart = this.parser.startPosition();
        
        if (this.parser.currentToken == TokenNamenew) {
            this.parser.consumeToken();
            refExpr.selector = TypeConstants.ANONYMOUS_METHOD;
            ASTHelper.setConstructorReference(refExpr);
            nameStart = this.parser.endPosition();
        } else if (this.parser.isIdentifier()) {
            char[] methodName = this.parser.identifier();
            refExpr.selector = methodName;
        }
        
        refExpr.lhs = lhs;
        refExpr.nameSourceStart = nameStart;
        refExpr.sourceStart = start;
        refExpr.sourceEnd = this.parser.endPosition();
        
        return refExpr;
    }
    
    protected Expression parseLambdaExpression(Expression parameter) {
        Argument[] args = null;
        if (parameter instanceof SingleNameReference) {
            char[] name = ((SingleNameReference) parameter).token;
            long pos = (((long) parameter.sourceStart) << 32) + parameter.sourceEnd;
            args = new Argument[] { new Argument(name, pos, null, 0) };
        }
        this.parser.consumeToken();
        
        LambdaExpression lambda = ASTHelper.createLambdaExpression(
            this.parser.compilationUnit.compilationResult, false);
        if (args != null) {
            lambda.setArguments(args);
        }
        lambda.arrowPosition = this.parser.endPosition();
        
        if (this.parser.currentToken == TokenNameLBRACE) {
            StatementParser stmtParser = new StatementParser(this.parser);
            Block block = stmtParser.parseBlock();
            lambda.setBody(block);
            lambda.sourceEnd = block.sourceEnd;
        } else {
            Expression body = parseExpression();
            lambda.setBody(body);
            lambda.sourceEnd = body.sourceEnd;
        }
        
        return lambda;
    }
    
    protected Expression parsePrimaryExpressionNoSuffix() {
        TerminalToken token = this.parser.currentToken;
        int start = this.parser.startPosition();
        
        if (token == TokenNameLPAREN) {
            return parseParenthesizedOrCastExpression();
        }
        
        if (token == TokenNamenew) {
            return parseAllocationExpression();
        }
        
        if (token == TokenNamenull) {
            this.parser.consumeToken();
            NullLiteral literal = new NullLiteral(start, this.parser.endPosition());
            return literal;
        }
        
        if (token == TokenNametrue) {
            this.parser.consumeToken();
            TrueLiteral literal = new TrueLiteral(start, this.parser.endPosition());
            return literal;
        }
        
        if (token == TokenNamefalse) {
            this.parser.consumeToken();
            FalseLiteral literal = new FalseLiteral(start, this.parser.endPosition());
            return literal;
        }
        
        if (token == TokenNameIntegerLiteral) {
            this.parser.consumeToken();
            char[] source = this.parser.scanner.getCurrentTokenSource();
            NumberLiteral literal = ASTHelper.createIntLiteral(
                source, start, this.parser.endPosition());
            return literal;
        }
        
        if (token == TokenNameLongLiteral) {
            this.parser.consumeToken();
            char[] source = this.parser.scanner.getCurrentTokenSource();
            LongLiteral literal = ASTHelper.createLongLiteral(
                source, start, this.parser.endPosition());
            return literal;
        }
        
        if (token == TokenNameFloatingPointLiteral) {
            this.parser.consumeToken();
            char[] source = this.parser.scanner.getCurrentTokenSource();
            FloatLiteral literal = ASTHelper.createFloatLiteral(
                source, start, this.parser.endPosition());
            return literal;
        }
        
        if (token == TokenNameDoubleLiteral) {
            this.parser.consumeToken();
            char[] source = this.parser.scanner.getCurrentTokenSource();
            DoubleLiteral literal = ASTHelper.createDoubleLiteral(
                source, start, this.parser.endPosition());
            return literal;
        }
        
        if (token == TokenNameCharacterLiteral) {
            this.parser.consumeToken();
            char[] source = this.parser.scanner.getCurrentTokenSource();
            CharLiteral literal = ASTHelper.createCharLiteral(
                source, start, this.parser.endPosition());
            return literal;
        }
        
        if (token == TokenNameStringLiteral) {
            this.parser.consumeToken();
            char[] source = this.parser.scanner.getCurrentTokenSource();
            StringLiteral literal = ASTHelper.createStringLiteral(
                source, start, this.parser.endPosition());
            return literal;
        }
        
        if (token == TokenNameTextBlock) {
            this.parser.consumeToken();
            char[] source = this.parser.scanner.getCurrentTokenSource();
            TextBlock literal = ASTHelper.createTextBlock(
                source, start, this.parser.endPosition(), 0);
            return literal;
        }
        
        if (token == TokenNamethis) {
            this.parser.consumeToken();
            ThisReference thisRef = new ThisReference(start, this.parser.endPosition());
            return thisRef;
        }
        
        if (token == TokenNamesuper) {
            this.parser.consumeToken();
            SuperReference superRef = new SuperReference(start, this.parser.endPosition());
            return superRef;
        }
        
        if (token == TokenNameAT) {
            Annotation annotation = parseAnnotation();
            return annotation;
        }
        
        if (token == TokenNameLESS) {
            return parseTypeReferenceOrLambda();
        }
        
        if (this.parser.isIdentifier()) {
            return parseNameOrTypeReference();
        }
        
        return null;
    }
    
    protected Expression parseParenthesizedOrCastExpression() {
        this.parser.consumeToken();
        
        if (isCastExpression()) {
            return parseCastExpression();
        }
        
        Expression expr = parseExpression();
        this.parser.match(TokenNameRPAREN);
        
        return expr;
    }
    
    protected boolean isCastExpression() {
        int savedPos = this.parser.scanner.currentPosition;
        int savedStart = this.parser.scanner.startPosition;
        TerminalToken savedToken = this.parser.currentToken;
        try {
            TypeReferenceParser typeParser = new TypeReferenceParser(this.parser);
            TypeReference typeRef = typeParser.parseTypeReference();
            if (typeRef != null && this.parser.currentToken == TokenNameRPAREN) {
                return true;
            }
            return false;
        } finally {
            this.parser.scanner.currentPosition = savedPos;
            this.parser.scanner.startPosition = savedStart;
            this.parser.currentToken = savedToken;
        }
    }
    
    protected CastExpression parseCastExpression() {
        TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
        this.parser.match(TokenNameRPAREN);
        
        Expression expr = parseUnaryExpression();
        
        CastExpression cast = new CastExpression(expr, typeRef);
        cast.sourceStart = typeRef.sourceStart;
        cast.sourceEnd = expr.sourceEnd;
        
        return cast;
    }
    
    public Expression parseAllocationExpression() {
        return parseAllocationExpression(-1, null);
    }
    
    public Expression parseAllocationExpression(int start, char[] typeName) {
        if (start < 0) {
            start = this.parser.startPosition();
        }
        this.parser.consumeToken();
        
        TypeReferenceParser typeParser = new TypeReferenceParser(this.parser);
        TypeReference typeRef = typeParser.parseTypeReference();
        
        if (this.parser.currentToken == TokenNameLESS) {
            typeRef = parseDiamondTypeReference(typeRef);
        }
        
        if (this.parser.currentToken == TokenNameLBRACKET) {
            return parseArrayAllocation(start, typeRef);
        }
        
        if (this.parser.currentToken == TokenNameLPAREN) {
            this.parser.consumeToken();
            Expression[] args = parseArgumentList();
            this.parser.match(TokenNameRPAREN);
            
            if (this.parser.currentToken == TokenNameLBRACE) {
                return parseAnonymousClassAllocation(start, typeRef);
            }
            
            AllocationExpression alloc = new AllocationExpression();
            alloc.type = typeRef;
            alloc.sourceStart = start;
            alloc.arguments = args;
            alloc.sourceEnd = this.parser.endPosition();
            return alloc;
        }
        
        if (this.parser.currentToken == TokenNameLBRACE) {
            return parseAnonymousClassAllocation(start, typeRef);
        }
        
        AllocationExpression alloc = new AllocationExpression();
        alloc.type = typeRef;
        alloc.sourceStart = start;
        alloc.sourceEnd = this.parser.endPosition();
        return alloc;
    }
    
    protected Expression parseArrayAllocation(int start, TypeReference typeRef) {
        ArrayAllocationExpression alloc = new ArrayAllocationExpression();
        alloc.type = typeRef;
        alloc.sourceStart = start;
        
        List<Expression> dimensions = new ArrayList<>();
        List<int[]> positions = new ArrayList<>();
        
        while (this.parser.currentToken == TokenNameLBRACKET) {
            this.parser.consumeToken();
            if (this.parser.currentToken == TokenNameRBRACKET) {
                this.parser.consumeToken();
                break;
            }
            Expression dimExpr = parseExpression();
            int dimEnd = this.parser.endPosition();
            dimensions.add(dimExpr);
            positions.add(new int[] { dimExpr.sourceStart, dimEnd });
            this.parser.match(TokenNameRBRACKET);
        }
        
        alloc.dimensions = dimensions.toArray(new Expression[0]);
        
        while (this.parser.currentToken == TokenNameLBRACKET) {
            this.parser.consumeToken();
            this.parser.match(TokenNameRBRACKET);
        }
        
        alloc.sourceEnd = this.parser.endPosition();
        
        return alloc;
    }
    
    protected TypeReference parseDiamondTypeReference(TypeReference baseType) {
        this.parser.consumeToken();
        this.parser.match(TokenNameGREATER);
        
        if (baseType instanceof SingleTypeReference) {
            SingleTypeReference singleRef = (SingleTypeReference) baseType;
            ParameterizedSingleTypeReference paramRef = new ParameterizedSingleTypeReference(
                singleRef.token, TypeReference.NO_TYPE_ARGUMENTS, 0,
                (((long) singleRef.sourceStart) << 32) + singleRef.sourceEnd);
            paramRef.bits |= ASTNode.IsDiamond;
            return paramRef;
        }
        
        return baseType;
    }
    
    protected Expression parseAnonymousClassAllocation(int start, TypeReference typeRef) {
        this.parser.match(TokenNameLBRACE);
        
        TypeDeclaration anonymousType = new TypeDeclaration(
            this.parser.compilationUnit.compilationResult);
        anonymousType.bits |= ASTNode.IsAnonymousType;
        anonymousType.name = CharOperation.NO_CHAR;
        anonymousType.declarationSourceStart = start;
        anonymousType.bodyStart = this.parser.startPosition();
        
        TypeDeclarationParser typeParser = new TypeDeclarationParser(this.parser);
        typeParser.parseClassBody(anonymousType);
        
        QualifiedAllocationExpression alloc = new QualifiedAllocationExpression(anonymousType);
        alloc.type = typeRef;
        alloc.sourceStart = start;
        alloc.sourceEnd = this.parser.endPosition();
        
        return alloc;
    }
    
    protected TypeReference toTypeReference(Expression expr) {
        if (expr instanceof TypeReference) {
            return (TypeReference) expr;
        }
        if (expr instanceof SingleNameReference) {
            SingleNameReference snr = (SingleNameReference) expr;
            return new SingleTypeReference(snr.token, snr.sourceStart);
        }
        if (expr instanceof QualifiedNameReference) {
            QualifiedNameReference qnr = (QualifiedNameReference) expr;
            return new QualifiedTypeReference(qnr.tokens, qnr.sourcePositions);
        }
        return null;
    }
    
    protected Expression parseFieldAccess(Expression receiver) {
        this.parser.consumeToken();
        
        if (this.parser.currentToken == TokenNameclass) {
            this.parser.consumeToken();
            return ASTHelper.createClassLiteralAccess(receiver.sourceStart, 
                toTypeReference(receiver));
        }
        
        if (this.parser.currentToken == TokenNamethis) {
            this.parser.consumeToken();
            return new QualifiedThisReference(toTypeReference(receiver), 
                receiver.sourceStart, this.parser.endPosition());
        }
        
        if (this.parser.currentToken == TokenNamesuper) {
            this.parser.consumeToken();
            return new QualifiedSuperReference(toTypeReference(receiver), 
                receiver.sourceStart, this.parser.endPosition());
        }
        
        if (this.parser.currentToken == TokenNamenew) {
            return parseAllocationExpression();
        }
        
        if (this.parser.isIdentifier()) {
            char[] fieldName = this.parser.identifier();
            
            FieldReference fieldRef = new FieldReference(fieldName, 
                (((long) receiver.sourceStart) << 32) + this.parser.endPosition());
            fieldRef.receiver = receiver;
            return fieldRef;
        }
        
        return receiver;
    }
    
    protected Expression parseArrayAccess(Expression array) {
        this.parser.consumeToken();
        
        Expression index = parseExpression();
        this.parser.match(TokenNameRBRACKET);
        
        ArrayReference arrayRef = new ArrayReference(array, index);
        arrayRef.sourceStart = array.sourceStart;
        arrayRef.sourceEnd = this.parser.endPosition();
        
        return arrayRef;
    }
    
    protected Expression parseMethodInvocation(Expression receiver) {
        this.parser.consumeToken();
        
        MessageSend messageSend = new MessageSend();
        messageSend.sourceStart = receiver.sourceStart;
        
        if (receiver instanceof FieldReference) {
            FieldReference fieldRef = (FieldReference) receiver;
            messageSend.selector = fieldRef.token;
            messageSend.receiver = fieldRef.receiver;
        } else if (receiver instanceof SingleNameReference) {
            messageSend.selector = ((SingleNameReference) receiver).token;
            messageSend.receiver = (NameReference) receiver;
        }
        
        if (receiver instanceof NameReference && messageSend.receiver == null) {
            messageSend.receiver = (NameReference) receiver;
        }
        
        messageSend.arguments = parseArgumentList();
        this.parser.match(TokenNameRPAREN);
        
        messageSend.sourceEnd = this.parser.endPosition();
        
        return messageSend;
    }
    
    public Expression[] parseArgumentList() {
        if (this.parser.currentToken == TokenNameRPAREN) {
            return null;
        }
        
        List<Expression> args = new ArrayList<>();
        
        Expression arg = parseExpression();
        if (arg != null) {
            args.add(arg);
        }
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            arg = parseExpression();
            if (arg != null) {
                args.add(arg);
            }
        }
        
        return args.isEmpty() ? null : args.toArray(new Expression[0]);
    }
    
    protected Expression parseNameOrTypeReference() {
        int start = this.parser.startPosition();
        char[] name = this.parser.identifier();
        
        if (this.parser.currentToken == TokenNameCOLON_COLON) {
            return parseReferenceExpression(start, name);
        }
        
        if (this.parser.currentToken == TokenNameARROW) {
            return parseLambdaExpression(start, name);
        }
        
        SingleNameReference nameRef = new SingleNameReference(name, 
            (((long) start) << 32) + this.parser.endPosition());
        
        return nameRef;
    }
    
    protected Expression parseReferenceExpression(int start, char[] name) {
        this.parser.consumeToken();
        
        ReferenceExpression refExpr = ASTHelper.createReferenceExpression(this.parser.scanner);
        refExpr.sourceStart = start;
        
        if (this.parser.currentToken == TokenNamenew) {
            this.parser.consumeToken();
            ASTHelper.setConstructorReference(refExpr);
        } else if (this.parser.isIdentifier()) {
            char[] methodName = this.parser.identifier();
            refExpr.selector = methodName;
        }
        
        refExpr.sourceEnd = this.parser.endPosition();
        
        return refExpr;
    }
    
    protected Expression parseLambdaExpression(int start, char[] name) {
        this.parser.consumeToken();
        
        LambdaExpression lambda = ASTHelper.createLambdaExpression(this.parser.compilationUnit.compilationResult, false);
        lambda.sourceStart = start;
        
        Argument[] args = new Argument[1];
        args[0] = new Argument(name, 0, null, 0);
        lambda.arguments = args;
        
        if (this.parser.currentToken == TokenNameLBRACE) {
            StatementParser stmtParser = new StatementParser(this.parser);
            lambda.body = stmtParser.parseBlock();
        } else {
            lambda.body = parseExpression();
        }
        
        lambda.sourceEnd = this.parser.endPosition();
        
        return lambda;
    }
    
    protected Expression parseTypeReferenceOrLambda() {
        return parseNameOrTypeReference();
    }
    
    public Annotation parseAnnotation() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        if (this.parser.currentToken == TokenNameinterface) {
            this.parser.scanner.currentPosition--;
            return null;
        }
        
        TypeReferenceParser typeParser = new TypeReferenceParser(this.parser);
        TypeReference typeRef = typeParser.parseTypeReference();
        
        if (this.parser.currentToken != TokenNameLPAREN) {
            MarkerAnnotation marker = new MarkerAnnotation(typeRef, start);
            marker.declarationSourceEnd = this.parser.endPosition();
            return marker;
        }
        
        this.parser.consumeToken();
        
        if (this.parser.currentToken == TokenNameRPAREN) {
            this.parser.consumeToken();
            NormalAnnotation normal = new NormalAnnotation(typeRef, start);
            normal.declarationSourceEnd = this.parser.endPosition();
            return normal;
        }
        
        MemberValuePair[] pairs = parseAnnotationMemberValuePairs();
        this.parser.match(TokenNameRPAREN);
        
        if (pairs != null && pairs.length == 1 && pairs[0].name == null) {
            SingleMemberAnnotation single = new SingleMemberAnnotation(typeRef, start);
            single.memberValue = pairs[0].value;
            single.declarationSourceEnd = this.parser.endPosition();
            return single;
        }
        
        NormalAnnotation normal = new NormalAnnotation(typeRef, start);
        normal.memberValuePairs = pairs;
        normal.declarationSourceEnd = this.parser.endPosition();
        
        return normal;
    }
    
    protected MemberValuePair[] parseAnnotationMemberValuePairs() {
        List<MemberValuePair> pairs = new ArrayList<>();
        
        MemberValuePair pair = parseAnnotationMemberValuePair();
        if (pair != null) {
            pairs.add(pair);
        }
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            pair = parseAnnotationMemberValuePair();
            if (pair != null) {
                pairs.add(pair);
            }
        }
        
        return pairs.isEmpty() ? null : pairs.toArray(new MemberValuePair[0]);
    }
    
    protected MemberValuePair parseAnnotationMemberValuePair() {
        int start = this.parser.startPosition();
        if (!this.parser.isIdentifier()) {
            Expression value = parseAnnotationValue();
            return new MemberValuePair(null, start, start, value);
        }
        
        TerminalToken next = this.parser.peekToken();
        if (next == TokenNameEQUAL) {
            char[] name = this.parser.identifier();
            this.parser.match(TokenNameEQUAL);
            Expression value = parseAnnotationValue();
            return new MemberValuePair(name, start, start + name.length, value);
        }
        
        Expression value = parseExpression();
        return new MemberValuePair(null, start, start, value);
    }
    
    public Expression parseAnnotationValue() {
        if (this.parser.currentToken == TokenNameAT) {
            return parseAnnotation();
        }
        
        if (this.parser.currentToken == TokenNameLBRACE) {
            return parseArrayInitializer();
        }
        
        return parseExpression();
    }
    
    public Expression parseArrayInitializer() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        List<Expression> exprs = new ArrayList<>();
        if (this.parser.currentToken != TokenNameRBRACE) {
            Expression expr = parseAnnotationValue();
            if (expr != null) {
                exprs.add(expr);
            }
            while (this.parser.currentToken == TokenNameCOMMA) {
                this.parser.consumeToken();
                expr = parseAnnotationValue();
                if (expr != null) {
                    exprs.add(expr);
                }
            }
        }
        
        this.parser.match(TokenNameRBRACE);
        
        ArrayInitializer init = new ArrayInitializer();
        init.expressions = exprs.isEmpty() ? null : exprs.toArray(new Expression[0]);
        init.sourceStart = start;
        init.sourceEnd = this.parser.endPosition();
        return init;
    }
}
