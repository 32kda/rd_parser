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

public class MemberParser {
    
    protected final RDParser parser;
    
    public MemberParser(RDParser parser) {
        this.parser = parser;
    }
    
    public Object parseMember(int modifiers, List<Annotation> annotations) {
        int start = this.parser.modifiersSourceStart;
        if (start < 0) {
            start = this.parser.startPosition();
        }
        
        TypeParameterParser typeParamParser = new TypeParameterParser(this.parser);
        TypeParameter[] typeParameters = typeParamParser.parse();
        
        if (this.parser.currentToken == TokenNamevoid) {
            return parseMethodDeclaration(start, modifiers, annotations, typeParameters, true);
        }
        
        if (this.parser.currentToken == TokenNameIdentifier) {
            TerminalToken next = this.parser.peekToken();
            if (next == TokenNameLPAREN) {
                char[] selector = this.parser.identifier();
                return parseConstructorDeclaration(start, modifiers, annotations, typeParameters, selector);
            }
            
            TypeReference typeRef = tryParseTypeReference();
            
            if (this.parser.isIdentifier()) {
                if (isFieldDeclaration()) {
                    return parseFieldDeclaration(start, modifiers, annotations, typeRef);
                } else {
                    return parseMethodDeclaration(start, modifiers, annotations, typeParameters, typeRef);
                }
            }
            
            if (this.parser.currentToken == TokenNameDOT) {
                while (this.parser.currentToken == TokenNameDOT) {
                    this.parser.consumeToken();
                    if (this.parser.isIdentifier()) {
                        this.parser.consumeToken();
                    }
                }
                typeRef = tryParseTypeReference();
                
                if (this.parser.isIdentifier()) {
                    if (isFieldDeclaration()) {
                        return parseFieldDeclaration(start, modifiers, annotations, typeRef);
                    } else {
                        return parseMethodDeclaration(start, modifiers, annotations, typeParameters, typeRef);
                    }
                }
            }
        }
        
        if (isPrimitiveTypeToken(this.parser.currentToken)) {
            TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
            
            if (this.parser.isIdentifier()) {
                if (isFieldDeclaration()) {
                    return parseFieldDeclaration(start, modifiers, annotations, typeRef);
                } else {
                    return parseMethodDeclaration(start, modifiers, annotations, typeParameters, typeRef);
                }
            }
        }
        
        this.parser.synchronize(RecoveryHandler.SYNC_MEMBER);
        return null;
    }
    
    protected boolean isFieldDeclaration() {
        TerminalToken next = this.parser.peekToken();
        return next == TokenNameSEMICOLON || 
               next == TokenNameEQUAL || 
               next == TokenNameCOMMA ||
               next == TokenNameLBRACKET;
    }
    
    protected boolean isPrimitiveTypeToken(TerminalToken token) {
        return token == TokenNameboolean || token == TokenNamebyte ||
               token == TokenNamechar || token == TokenNameshort ||
               token == TokenNameint || token == TokenNamelong ||
               token == TokenNamefloat || token == TokenNamedouble ||
               token == TokenNamevoid;
    }
    
    protected TypeReference tryParseTypeReference() {
        TypeReferenceParser typeParser = new TypeReferenceParser(this.parser);
        TypeReference typeRef = typeParser.parseTypeReference();
        if (typeRef != null) {
            int dims = typeParser.parseDimensions();
            if (dims > 0) {
                typeRef = typeParser.augmentTypeWithDimensions(typeRef, dims);
            }
        }
        return typeRef;
    }
    
    protected MethodDeclaration parseMethodDeclaration(int start, int modifiers, 
            List<Annotation> annotations, TypeParameter[] typeParameters, boolean isVoid) {
        
        TypeReference returnType;
        if (isVoid) {
            returnType = new SingleTypeReference(TypeConstants.VOID,
                (((long) this.parser.startPosition()) << 32) + this.parser.endPosition());
            this.parser.consumeToken();
        } else {
            returnType = tryParseTypeReference();
        }
        
        return parseMethodDeclaration(start, modifiers, annotations, typeParameters, returnType);
    }
    
    protected MethodDeclaration parseMethodDeclaration(int start, int modifiers, 
            List<Annotation> annotations, TypeParameter[] typeParameters, TypeReference returnType) {
        
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            this.parser.synchronize(RecoveryHandler.SYNC_MEMBER);
            return null;
        }
        
        char[] selector = this.parser.identifier();
        
        MethodDeclaration method = new MethodDeclaration(this.parser.compilationUnit.compilationResult);
        method.modifiers = modifiers;
        method.annotations = annotations.isEmpty() ? null : annotations.toArray(new Annotation[0]);
        method.typeParameters = typeParameters;
        method.returnType = returnType;
        method.selector = selector;
        method.sourceStart = start;
        method.declarationSourceStart = start;
        
        if (!this.parser.match(TokenNameLPAREN)) {
            this.parser.synchronize(RecoveryHandler.SYNC_MEMBER);
            return method;
        }
        
        method.arguments = parseParameterList();
        this.parser.match(TokenNameRPAREN);
        
        if (this.parser.currentToken == TokenNamethrows) {
            this.parser.consumeToken();
            method.thrownExceptions = parseExceptionList();
        }
        
        if (this.parser.currentToken == TokenNamedefault && 
            (modifiers & ClassFileConstants.AccAbstract) != 0) {
            this.parser.consumeToken();
            // default value for annotation type members - handled separately
        }
        
        if ((modifiers & (ClassFileConstants.AccAbstract | ClassFileConstants.AccNative)) != 0) {
            this.parser.match(TokenNameSEMICOLON);
            method.bodyStart = method.declarationSourceEnd = this.parser.endPosition();
        } else if (this.parser.currentToken == TokenNameLBRACE) {
            if (!this.parser.diet || this.parser.dietInt > 0) {
                parseMethodBody(method);
            } else {
                this.parser.match(TokenNameLBRACE);
                skipMethodBody(method);
            }
        } else {
            this.parser.match(TokenNameSEMICOLON);
            method.bodyStart = method.declarationSourceEnd = this.parser.endPosition();
        }
        
        return method;
    }
    
    protected ConstructorDeclaration parseConstructorDeclaration(int start, int modifiers,
            List<Annotation> annotations, TypeParameter[] typeParameters, char[] selector) {
        
        ConstructorDeclaration constructor = new ConstructorDeclaration(
            this.parser.compilationUnit.compilationResult);
        constructor.modifiers = modifiers;
        constructor.annotations = annotations.isEmpty() ? null : annotations.toArray(new Annotation[0]);
        constructor.typeParameters = typeParameters;
        constructor.selector = selector;
        constructor.sourceStart = start;
        constructor.declarationSourceStart = start;
        
        if (!this.parser.match(TokenNameLPAREN)) {
            this.parser.synchronize(RecoveryHandler.SYNC_MEMBER);
            return constructor;
        }
        
        constructor.arguments = parseParameterList();
        this.parser.match(TokenNameRPAREN);
        
        if (this.parser.currentToken == TokenNamethrows) {
            this.parser.consumeToken();
            constructor.thrownExceptions = parseExceptionList();
        }
        
        if (this.parser.currentToken == TokenNameLBRACE) {
            parseConstructorBody(constructor);
        } else {
            this.parser.match(TokenNameSEMICOLON);
        }
        
        return constructor;
    }
    
    protected Argument[] parseParameterList() {
        List<Argument> arguments = new ArrayList<>();
        
        if (this.parser.currentToken != TokenNameRPAREN) {
            Argument arg = parseParameter();
            if (arg != null) {
                arguments.add(arg);
            }
            
            while (this.parser.currentToken == TokenNameCOMMA) {
                this.parser.consumeToken();
                arg = parseParameter();
                if (arg != null) {
                    arguments.add(arg);
                }
            }
        }
        
        return arguments.isEmpty() ? null : arguments.toArray(new Argument[0]);
    }
    
    protected Argument parseParameter() {
        this.parser.resetModifiers();
        parseParameterModifiers();
        
        int start = this.parser.modifiersSourceStart;
        if (start < 0) {
            start = this.parser.startPosition();
        }
        
        TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
        if (typeRef == null) {
            this.parser.synchronize(RecoveryHandler.SYNC_EXPRESSION);
            return null;
        }
        
        TypeReferenceParser dimParser = new TypeReferenceParser(this.parser);
        int dimensions = dimParser.parseDimensions();
        if (dimensions > 0) {
            typeRef = dimParser.augmentTypeWithDimensions(typeRef, dimensions);
        }
        
        boolean isVarargs = false;
        if (this.parser.currentToken == TokenNameELLIPSIS) {
            isVarargs = true;
            this.parser.consumeToken();
            typeRef.bits |= ASTNode.IsVarArgs;
        }
        
        if (!this.parser.isIdentifier()) {
            if (isVarargs || typeRef != null) {
                Argument arg = new Argument(CharOperation.NO_CHAR, 
                    (((long) start) << 32) + this.parser.endPosition(), 
                    typeRef, this.parser.modifiers);
                arg.bits |= ASTNode.IsArgument;
                if (isVarargs) {
                    arg.type = typeRef;
                }
                return arg;
            }
            this.parser.reportSyntaxError(TokenNameIdentifier);
            return null;
        }
        
        char[] name = this.parser.identifier();
        
        Argument argument = new Argument(name, 
            (((long) start) << 32) + this.parser.endPosition(), 
            typeRef, this.parser.modifiers);
        argument.bits |= ASTNode.IsArgument;
        argument.annotations = this.parser.annotations.isEmpty() ? null : 
            this.parser.annotations.toArray(new Annotation[0]);
        
        return argument;
    }
    
    protected void parseParameterModifiers() {
        while (true) {
            TerminalToken token = this.parser.currentToken;
            
            if (token == TokenNamefinal) {
                this.parser.consumeToken();
                this.parser.checkAndSetModifiers(ClassFileConstants.AccFinal);
            } else if (token == TokenNameAT) {
                ExpressionParser exprParser = new ExpressionParser(this.parser);
                Annotation annotation = exprParser.parseAnnotation();
                if (annotation != null) {
                    this.parser.annotations.add(annotation);
                }
            } else {
                break;
            }
        }
    }
    
    protected TypeReference[] parseExceptionList() {
        List<TypeReference> exceptions = new ArrayList<>();
        
        TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
        if (typeRef != null) {
            exceptions.add(typeRef);
        }
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
            if (typeRef != null) {
                exceptions.add(typeRef);
            }
        }
        
        return exceptions.isEmpty() ? null : exceptions.toArray(new TypeReference[0]);
    }
    
    protected void parseMethodBody(MethodDeclaration method) {
        this.parser.match(TokenNameLBRACE);
        method.bodyStart = this.parser.startPosition();
        
        StatementParser stmtParser = new StatementParser(this.parser);
        method.statements = stmtParser.parseStatements();
        
        this.parser.match(TokenNameRBRACE);
        method.bodyEnd = this.parser.endPosition();
        method.declarationSourceEnd = method.bodyEnd;
    }
    
    protected void skipMethodBody(MethodDeclaration method) {
        int braceBalance = 1;
        while (this.parser.currentToken != TokenNameEOF && braceBalance > 0) {
            TerminalToken token = this.parser.currentToken;
            if (token == TokenNameLBRACE) {
                braceBalance++;
            } else if (token == TokenNameRBRACE) {
                braceBalance--;
            }
            this.parser.consumeToken();
        }
        method.bodyEnd = this.parser.endPosition();
        method.declarationSourceEnd = method.bodyEnd;
    }
    
    protected void parseConstructorBody(ConstructorDeclaration constructor) {
        this.parser.match(TokenNameLBRACE);
        constructor.bodyStart = this.parser.startPosition();
        
        StatementParser stmtParser = new StatementParser(this.parser);
        
        if (this.parser.currentToken == TokenNamesuper) {
            constructor.constructorCall = parseExplicitConstructorCall();
            if (constructor.constructorCall != null) {
                constructor.constructorCall.sourceStart = constructor.bodyStart;
            }
        } else if (this.parser.currentToken == TokenNamethis &&
                   this.parser.peekToken() == TokenNameLPAREN) {
            constructor.constructorCall = parseExplicitConstructorCall();
            if (constructor.constructorCall != null) {
                constructor.constructorCall.sourceStart = constructor.bodyStart;
            }
        }
        
        constructor.statements = stmtParser.parseStatements();
        
        this.parser.match(TokenNameRBRACE);
        constructor.bodyEnd = this.parser.endPosition();
        constructor.declarationSourceEnd = constructor.bodyEnd;
    }
    
    protected ExplicitConstructorCall parseExplicitConstructorCall() {
        int start = this.parser.startPosition();
        int kind = this.parser.currentToken == TokenNamesuper ?
            ExplicitConstructorCall.Super : ExplicitConstructorCall.This;
        
        this.parser.consumeToken();
        
        ExplicitConstructorCall call = new ExplicitConstructorCall(kind);
        call.sourceStart = start;
        
        this.parser.match(TokenNameLPAREN);
        
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        call.arguments = exprParser.parseArgumentList();
        
        this.parser.match(TokenNameRPAREN);
        this.parser.match(TokenNameSEMICOLON);
        
        call.sourceEnd = this.parser.endPosition();
        
        return call;
    }
    
    protected FieldDeclaration parseFieldDeclaration(int start, int modifiers,
            List<Annotation> annotations, TypeReference typeRef) {
        
        List<FieldDeclaration> fields = new ArrayList<>();
        
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            this.parser.synchronize(RecoveryHandler.SYNC_MEMBER);
            return null;
        }
        
        char[] name = this.parser.identifier();
        long pos = (((long) this.parser.startPosition()) << 32) + this.parser.endPosition();
        
        FieldDeclaration field = new FieldDeclaration(name, (int) (pos >>> 32), (int) pos);
        field.modifiers = modifiers;
        field.annotations = annotations.isEmpty() ? null : annotations.toArray(new Annotation[0]);
        field.type = typeRef;
        field.declarationSourceStart = start;
        
        TypeReferenceParser typeRefParser = new TypeReferenceParser(this.parser);
        int dimensions = typeRefParser.parseDimensions();
        if (dimensions > 0) {
            field.type = typeRefParser.augmentTypeWithDimensions(typeRef, dimensions);
        }
        
        if (this.parser.currentToken == TokenNameEQUAL) {
            this.parser.consumeToken();
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            field.initialization = exprParser.parseExpression();
        }
        
        field.sourceEnd = this.parser.endPosition();
        field.declarationSourceEnd = field.sourceEnd;
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            
            FieldDeclaration additionalField = parseAdditionalField(field);
            if (additionalField != null) {
                fields.add(additionalField);
            }
        }
        
        this.parser.match(TokenNameSEMICOLON);
        
        if (!fields.isEmpty()) {
            fields.add(0, field);
            return createMultiFieldDeclaration(fields, start);
        }
        
        return field;
    }
    
    protected FieldDeclaration parseAdditionalField(FieldDeclaration firstField) {
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            return null;
        }
        
        char[] name = this.parser.identifier();
        long pos = (((long) this.parser.startPosition()) << 32) + this.parser.endPosition();
        
        FieldDeclaration field = new FieldDeclaration(name, (int) (pos >>> 32), (int) pos);
        field.modifiers = firstField.modifiers;
        field.type = firstField.type;
        field.declarationSourceStart = firstField.declarationSourceStart;
        
        TypeReferenceParser typeRefParser = new TypeReferenceParser(this.parser);
        int dimensions = typeRefParser.parseDimensions();
        
        if (this.parser.currentToken == TokenNameEQUAL) {
            this.parser.consumeToken();
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            field.initialization = exprParser.parseExpression();
        }
        
        field.sourceEnd = this.parser.endPosition();
        field.declarationSourceEnd = field.sourceEnd;
        
        return field;
    }
    
    protected FieldDeclaration createMultiFieldDeclaration(List<FieldDeclaration> fields, int start) {
        if (fields.size() == 1) {
            return fields.get(0);
        }
        
        FieldDeclaration first = fields.get(0);
        int end = fields.get(fields.size() - 1).sourceEnd;
        first.declarationSourceEnd = end;
        
        return first;
    }
}
