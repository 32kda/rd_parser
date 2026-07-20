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

public class TypeDeclarationParser {
    
    protected final RDParser parser;
    
    public TypeDeclarationParser(RDParser parser) {
        this.parser = parser;
    }
    
    public TypeDeclaration parse() {
        this.parser.resetModifiers();
        
        parseModifiers();
        List<Annotation> annotations = new ArrayList<>(this.parser.annotations);
        int modifiers = this.parser.modifiers;
        int modifiersStart = this.parser.modifiersSourceStart;
        
        TerminalToken typeToken = this.parser.currentToken;
        int typeStart = this.parser.startPosition();
        
        TypeDeclaration typeDecl = new TypeDeclaration(this.parser.compilationUnit.compilationResult);
        typeDecl.modifiers = modifiers;
        typeDecl.annotations = annotations.isEmpty() ? null : annotations.toArray(new Annotation[0]);
        
        if (modifiersStart >= 0) {
            typeDecl.declarationSourceStart = modifiersStart;
        } else {
            typeDecl.declarationSourceStart = typeStart;
        }
        
        if (typeToken == TokenNameclass || typeToken == TokenNameRestrictedIdentifierrecord) {
            if (typeToken == TokenNameRestrictedIdentifierrecord || 
                (this.parser.isParsingJava14Plus() && isRecordKeyword())) {
                typeDecl.modifiers |= ExtraCompilerModifiers.AccRecord;
                this.parser.consumeToken();
            } else {
                this.parser.consumeToken();
            }
            parseClassDeclaration(typeDecl);
        } else if (typeToken == TokenNameinterface) {
            typeDecl.modifiers |= ClassFileConstants.AccInterface;
            this.parser.consumeToken();
            parseInterfaceDeclaration(typeDecl);
        } else if (typeToken == TokenNameenum) {
            typeDecl.modifiers |= ClassFileConstants.AccEnum;
            this.parser.consumeToken();
            parseEnumDeclaration(typeDecl);
        } else if (typeToken == TokenNameAT && this.parser.peekToken() == TokenNameinterface) {
            this.parser.consumeToken();
            this.parser.consumeToken();
            typeDecl.modifiers |= ClassFileConstants.AccAnnotation;
            parseAnnotationDeclaration(typeDecl);
        } else {
            return null;
        }
        
        return typeDecl;
    }
    
    protected boolean isRecordKeyword() {
        char[] source = this.parser.scanner.getCurrentIdentifierSource();
        return source.length == 6 && 
               source[0] == 'r' && new String(source).equals("record");
    }
    
    protected void parseModifiers() {
        while (true) {
            TerminalToken token = this.parser.currentToken;
            if (token == TokenNameAT) {
                Annotation annotation = new ExpressionParser(this.parser).parseAnnotation();
                if (annotation != null) {
                    this.parser.annotations.add(annotation);
                }
            } else if (this.parser.isModifierToken(token)) {
                int start = this.parser.startPosition();
                this.parser.consumeToken();
                
                switch (token) {
                    case TokenNamepublic:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccPublic);
                        break;
                    case TokenNameprivate:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccPrivate);
                        break;
                    case TokenNameprotected:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccProtected);
                        break;
                    case TokenNamestatic:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccStatic);
                        break;
                    case TokenNamefinal:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccFinal);
                        break;
                    case TokenNameabstract:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccAbstract);
                        break;
                    case TokenNamenative:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccNative);
                        break;
                    case TokenNamesynchronized:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccSynchronized);
                        break;
                    case TokenNamevolatile:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccVolatile);
                        break;
                    case TokenNametransient:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccTransient);
                        break;
                    case TokenNamestrictfp:
                        this.parser.checkAndSetModifiers(ClassFileConstants.AccStrictfp);
                        break;
                    case TokenNamenon_sealed:
                        this.parser.checkAndSetModifiers(ExtraCompilerModifiers.AccNonSealed);
                        break;
                    case TokenNameRestrictedIdentifiersealed:
                        this.parser.checkAndSetModifiers(ExtraCompilerModifiers.AccSealed);
                        break;
                    default:
                        break;
                }
            } else {
                break;
            }
        }
    }
    
    protected void parseClassDeclaration(TypeDeclaration typeDecl) {
        char[] typeName = this.parser.scanner.getCurrentIdentifierSource();
        if (!this.parser.matchIdentifier()) {
            this.parser.synchronize(RecoveryHandler.SYNC_TYPE);
            return;
        }
        typeDecl.name = typeName;
        
        TypeParameterParser typeParamParser = new TypeParameterParser(this.parser);
        typeDecl.typeParameters = typeParamParser.parse();
        
        if (this.parser.currentToken == TokenNameextends) {
            this.parser.consumeToken();
            typeDecl.superclass = new TypeReferenceParser(this.parser).parseTypeReference();
        }
        
        if (this.parser.currentToken == TokenNameimplements) {
            this.parser.consumeToken();
            typeDecl.superInterfaces = parseInterfaceList();
        }
        
        if (this.parser.currentToken == TokenNameRestrictedIdentifierpermits) {
            this.parser.consumeToken();
            typeDecl.permittedTypes = parseInterfaceList();
        }
        
        if (!this.parser.match(TokenNameLBRACE)) {
            this.parser.synchronize(RecoveryHandler.SYNC_CLASS_BODY);
            if (this.parser.currentToken != TokenNameLBRACE) {
                return;
            }
        }
        
        typeDecl.bodyStart = this.parser.startPosition();
        parseClassBody(typeDecl);
    }
    
    protected void parseInterfaceDeclaration(TypeDeclaration typeDecl) {
        char[] typeName = this.parser.scanner.getCurrentIdentifierSource();
        if (!this.parser.matchIdentifier()) {
            this.parser.synchronize(RecoveryHandler.SYNC_TYPE);
            return;
        }
        typeDecl.name = typeName;
        
        TypeParameterParser typeParamParser = new TypeParameterParser(this.parser);
        typeDecl.typeParameters = typeParamParser.parse();
        
        if (this.parser.currentToken == TokenNameextends) {
            this.parser.consumeToken();
            typeDecl.superInterfaces = parseInterfaceList();
        }
        
        if (!this.parser.match(TokenNameLBRACE)) {
            this.parser.synchronize(RecoveryHandler.SYNC_CLASS_BODY);
            if (this.parser.currentToken != TokenNameLBRACE) {
                return;
            }
        }
        
        typeDecl.bodyStart = this.parser.startPosition();
        parseInterfaceBody(typeDecl);
    }
    
    protected void parseEnumDeclaration(TypeDeclaration typeDecl) {
        char[] typeName = this.parser.scanner.getCurrentIdentifierSource();
        if (!this.parser.matchIdentifier()) {
            this.parser.synchronize(RecoveryHandler.SYNC_TYPE);
            return;
        }
        typeDecl.name = typeName;
        
        if (this.parser.currentToken == TokenNameimplements) {
            this.parser.consumeToken();
            typeDecl.superInterfaces = parseInterfaceList();
        }
        
        if (!this.parser.match(TokenNameLBRACE)) {
            this.parser.synchronize(RecoveryHandler.SYNC_CLASS_BODY);
            if (this.parser.currentToken != TokenNameLBRACE) {
                return;
            }
        }
        
        typeDecl.bodyStart = this.parser.startPosition();
        parseEnumBody(typeDecl);
    }
    
    protected void parseAnnotationDeclaration(TypeDeclaration typeDecl) {
        char[] typeName = this.parser.scanner.getCurrentIdentifierSource();
        if (!this.parser.matchIdentifier()) {
            this.parser.synchronize(RecoveryHandler.SYNC_TYPE);
            return;
        }
        typeDecl.name = typeName;
        
        if (!this.parser.match(TokenNameLBRACE)) {
            this.parser.synchronize(RecoveryHandler.SYNC_CLASS_BODY);
            if (this.parser.currentToken != TokenNameLBRACE) {
                return;
            }
        }
        
        typeDecl.bodyStart = this.parser.startPosition();
        parseAnnotationBody(typeDecl);
    }
    
    protected TypeReference[] parseInterfaceList() {
        List<TypeReference> interfaces = new ArrayList<>();
        
        TypeReference typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
        if (typeRef != null) {
            interfaces.add(typeRef);
        }
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            typeRef = new TypeReferenceParser(this.parser).parseTypeReference();
            if (typeRef != null) {
                interfaces.add(typeRef);
            }
        }
        
        return interfaces.isEmpty() ? null : interfaces.toArray(new TypeReference[0]);
    }
    
    protected void parseClassBody(TypeDeclaration typeDecl) {
        List<FieldDeclaration> fields = new ArrayList<>();
        List<AbstractMethodDeclaration> methods = new ArrayList<>();
        List<TypeDeclaration> memberTypes = new ArrayList<>();
        
        this.parser.nestedType++;
        
        while (this.parser.currentToken != TokenNameEOF && 
               this.parser.currentToken != TokenNameRBRACE) {
            
            if (this.parser.currentToken == TokenNameSEMICOLON) {
                this.parser.consumeToken();
                continue;
            }
            
            if (this.parser.currentToken == TokenNameLBRACE) {
                Initializer initializer = parseInitializer();
                if (initializer != null) {
                    fields.add(initializer);
                }
                continue;
            }
            
            this.parser.resetModifiers();
            parseModifiers();
            
            if (this.parser.currentToken == TokenNameLBRACE) {
                Initializer initializer = parseInitializer();
                if (initializer != null) {
                    initializer.modifiers = this.parser.modifiers;
                    fields.add(initializer);
                }
                continue;
            }
            
            if (isTypeStart()) {
                TypeDeclaration memberType = parse();
                if (memberType != null) {
                    memberTypes.add(memberType);
                }
                continue;
            }
            
            if (this.parser.currentToken == TokenNameIdentifier ||
                this.parser.currentToken == TokenNamevoid ||
                this.parser.currentToken == TokenNameLESS ||
                this.parser.isModifierToken(this.parser.currentToken) ||
                isPrimitiveTypeToken(this.parser.currentToken) ||
                this.parser.currentToken == TokenNameLBRACE) {
                
                if (this.parser.currentToken == TokenNameLBRACE) {
                    Initializer initializer = parseInitializer();
                    if (initializer != null) {
                        initializer.modifiers = this.parser.modifiers;
                        fields.add(initializer);
                    }
                } else {
                    MemberParser memberParser = new MemberParser(this.parser);
                    Object member = memberParser.parseMember(this.parser.modifiers, this.parser.annotations);
                    
                    if (member instanceof FieldDeclaration) {
                        fields.add((FieldDeclaration) member);
                    } else if (member instanceof AbstractMethodDeclaration) {
                        methods.add((AbstractMethodDeclaration) member);
                    }
                }
                continue;
            }
            
            this.parser.synchronize(RecoveryHandler.SYNC_CLASS_BODY);
        }
        
        this.parser.match(TokenNameRBRACE);
        this.parser.nestedType--;
        
        typeDecl.bodyEnd = this.parser.endPosition();
        typeDecl.declarationSourceEnd = typeDecl.bodyEnd;
        
        if (!fields.isEmpty()) {
            typeDecl.fields = fields.toArray(new FieldDeclaration[0]);
        }
        if (!methods.isEmpty()) {
            typeDecl.methods = methods.toArray(new AbstractMethodDeclaration[0]);
        }
        if (!memberTypes.isEmpty()) {
            typeDecl.memberTypes = memberTypes.toArray(new TypeDeclaration[0]);
        }
    }
    
    protected void parseInterfaceBody(TypeDeclaration typeDecl) {
        List<FieldDeclaration> fields = new ArrayList<>();
        List<AbstractMethodDeclaration> methods = new ArrayList<>();
        List<TypeDeclaration> memberTypes = new ArrayList<>();
        
        this.parser.nestedType++;
        
        while (this.parser.currentToken != TokenNameEOF && 
               this.parser.currentToken != TokenNameRBRACE) {
            
            if (this.parser.currentToken == TokenNameSEMICOLON) {
                this.parser.consumeToken();
                continue;
            }
            
            this.parser.resetModifiers();
            parseModifiers();
            
            if (isTypeStart()) {
                TypeDeclaration memberType = parse();
                if (memberType != null) {
                    memberTypes.add(memberType);
                }
                continue;
            }
            
            TerminalToken token = this.parser.currentToken;
            
            if (token == TokenNameIdentifier || token == TokenNamevoid || 
                token == TokenNameLESS || this.parser.isModifierToken(token)) {
                
                MemberParser memberParser = new MemberParser(this.parser);
                Object member = memberParser.parseMember(this.parser.modifiers, this.parser.annotations);
                
                if (member instanceof FieldDeclaration) {
                    fields.add((FieldDeclaration) member);
                } else if (member instanceof AbstractMethodDeclaration) {
                    methods.add((AbstractMethodDeclaration) member);
                }
                continue;
            }
            
            this.parser.synchronize(RecoveryHandler.SYNC_CLASS_BODY);
        }
        
        this.parser.match(TokenNameRBRACE);
        this.parser.nestedType--;
        
        typeDecl.bodyEnd = this.parser.endPosition();
        typeDecl.declarationSourceEnd = typeDecl.bodyEnd;
        
        if (!fields.isEmpty()) {
            typeDecl.fields = fields.toArray(new FieldDeclaration[0]);
        }
        if (!methods.isEmpty()) {
            typeDecl.methods = methods.toArray(new AbstractMethodDeclaration[0]);
        }
        if (!memberTypes.isEmpty()) {
            typeDecl.memberTypes = memberTypes.toArray(new TypeDeclaration[0]);
        }
    }
    
    protected void parseEnumBody(TypeDeclaration typeDecl) {
        List<FieldDeclaration> enumConstants = new ArrayList<>();
        List<FieldDeclaration> fields = new ArrayList<>();
        List<AbstractMethodDeclaration> methods = new ArrayList<>();
        List<TypeDeclaration> memberTypes = new ArrayList<>();
        
        while (this.parser.currentToken != TokenNameEOF && 
               this.parser.currentToken != TokenNameRBRACE &&
               this.parser.currentToken != TokenNameCOMMA) {
            
            if (this.parser.currentToken == TokenNameSEMICOLON) {
                this.parser.consumeToken();
                break;
            }
            
            if (this.parser.currentToken == TokenNameAT) {
                this.parser.resetModifiers();
                parseModifiers();
            }
            
            if (this.parser.isIdentifier()) {
                FieldDeclaration enumConst = parseEnumConstant();
                if (enumConst != null) {
                    enumConstants.add(enumConst);
                }
                
                if (this.parser.currentToken == TokenNameCOMMA) {
                    this.parser.consumeToken();
                    continue;
                }
            }
        }
        
        typeDecl.fields = enumConstants.toArray(new FieldDeclaration[0]);
        
        parseClassBody(typeDecl);
    }
    
    protected FieldDeclaration parseEnumConstant() {
        int start = this.parser.startPosition();
        char[] name = this.parser.identifier();
        
        FieldDeclaration field = new FieldDeclaration(name, start, this.parser.endPosition());
        field.modifiers = ClassFileConstants.AccPublic | ClassFileConstants.AccStatic | 
                          ClassFileConstants.AccFinal | ClassFileConstants.AccEnum;
        
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        
        if (this.parser.currentToken == TokenNameLPAREN) {
            this.parser.consumeToken();
            field.initialization = exprParser.parseAllocationExpression(start, name);
            this.parser.match(TokenNameRPAREN);
        }
        
        if (this.parser.currentToken == TokenNameLBRACE) {
            TypeDeclaration anonymousType = new TypeDeclaration(
                this.parser.compilationUnit.compilationResult);
            anonymousType.bits |= ASTNode.IsAnonymousType;
            anonymousType.name = CharOperation.NO_CHAR;
            anonymousType.declarationSourceStart = start;
            anonymousType.bodyStart = this.parser.startPosition();
            
            this.parser.match(TokenNameLBRACE);
            parseClassBody(anonymousType);
            
            QualifiedAllocationExpression alloc = new QualifiedAllocationExpression(anonymousType);
            alloc.type = new SingleTypeReference(name, (((long) start) << 32) + this.parser.endPosition());
            field.initialization = alloc;
        }
        
        field.declarationSourceEnd = this.parser.endPosition();
        return field;
    }
    
    protected void parseAnnotationBody(TypeDeclaration typeDecl) {
        List<AbstractMethodDeclaration> methods = new ArrayList<>();
        
        this.parser.nestedType++;
        
        while (this.parser.currentToken != TokenNameEOF && 
               this.parser.currentToken != TokenNameRBRACE) {
            
            if (this.parser.currentToken == TokenNameSEMICOLON) {
                this.parser.consumeToken();
                continue;
            }
            
            this.parser.resetModifiers();
            parseModifiers();
            
            if (this.parser.currentToken == TokenNameclass ||
                this.parser.currentToken == TokenNameinterface ||
                this.parser.currentToken == TokenNameenum) {
                TypeDeclaration memberType = parse();
                continue;
            }
            
            if (this.parser.isIdentifier()) {
                AnnotationMethodDeclaration method = parseAnnotationMethod();
                if (method != null) {
                    methods.add(method);
                }
                continue;
            }
            
            this.parser.synchronize(RecoveryHandler.SYNC_CLASS_BODY);
        }
        
        this.parser.match(TokenNameRBRACE);
        this.parser.nestedType--;
        
        typeDecl.bodyEnd = this.parser.endPosition();
        typeDecl.declarationSourceEnd = typeDecl.bodyEnd;
        
        if (!methods.isEmpty()) {
            typeDecl.methods = methods.toArray(new AbstractMethodDeclaration[0]);
        }
    }
    
    protected AnnotationMethodDeclaration parseAnnotationMethod() {
        int start = this.parser.startPosition();
        
        TypeReference returnType = new TypeReferenceParser(this.parser).parseTypeReference();
        
        if (!this.parser.isIdentifier()) {
            this.parser.synchronize(RecoveryHandler.SYNC_MEMBER);
            return null;
        }
        
        char[] name = this.parser.identifier();
        
        AnnotationMethodDeclaration method = new AnnotationMethodDeclaration(
            this.parser.compilationUnit.compilationResult);
        method.modifiers = ClassFileConstants.AccPublic | ClassFileConstants.AccAbstract;
        method.returnType = returnType;
        method.selector = name;
        method.sourceStart = start;
        method.declarationSourceStart = start;
        
        this.parser.match(TokenNameLPAREN);
        this.parser.match(TokenNameRPAREN);
        
        if (this.parser.currentToken == TokenNamedefault) {
            this.parser.consumeToken();
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            method.defaultValue = exprParser.parseExpression();
        }
        
        this.parser.match(TokenNameSEMICOLON);
        
        method.sourceEnd = this.parser.endPosition();
        method.declarationSourceEnd = method.sourceEnd;
        
        return method;
    }
    
    protected Initializer parseInitializer() {
        int start = this.parser.startPosition();
        int modifiers = 0;
        
        StatementParser stmtParser = new StatementParser(this.parser);
        Block block = stmtParser.parseBlock();
        
        Initializer initializer = new Initializer(block, modifiers);
        initializer.declarationSourceStart = start;
        initializer.bodyStart = start;
        initializer.bodyEnd = this.parser.endPosition();
        initializer.declarationSourceEnd = initializer.bodyEnd;
        initializer.sourceStart = start;
        initializer.sourceEnd = initializer.bodyEnd;
        
        return initializer;
    }
    
    protected boolean isTypeStart() {
        TerminalToken token = this.parser.currentToken;
        return token == TokenNameclass || 
               token == TokenNameinterface ||
               token == TokenNameenum ||
               token == TokenNameRestrictedIdentifierrecord ||
               (token == TokenNameAT && this.parser.peekToken() == TokenNameinterface);
    }
    
    protected boolean isPrimitiveTypeToken(TerminalToken token) {
        return token == TokenNameboolean || token == TokenNamebyte ||
               token == TokenNamechar || token == TokenNameshort ||
               token == TokenNameint || token == TokenNamelong ||
               token == TokenNamefloat || token == TokenNamedouble ||
               token == TokenNamevoid;
    }
}
