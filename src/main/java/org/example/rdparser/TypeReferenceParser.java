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
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;

public class TypeReferenceParser {
    
    protected final RDParser parser;
    
    public TypeReferenceParser(RDParser parser) {
        this.parser = parser;
    }
    
    public TypeReference parseTypeReference() {
        return parseTypeReference(false);
    }
    
    public TypeReference parseTypeReference(boolean allowAnnotations) {
        int start = this.parser.startPosition();
        
        List<Annotation> leadingAnnotations = new ArrayList<>();
        if (allowAnnotations) {
            while (this.parser.currentToken == TokenNameAT) {
                ExpressionParser exprParser = new ExpressionParser(this.parser);
                Annotation annotation = exprParser.parseAnnotation();
                if (annotation != null) {
                    leadingAnnotations.add(annotation);
                }
            }
        }
        
        TypeReference typeRef = parseTypeReference0(start);
        
        if (typeRef != null && !leadingAnnotations.isEmpty()) {
            typeRef.annotations = new Annotation[][] { 
                leadingAnnotations.toArray(new Annotation[0]) 
            };
        }
        
        return typeRef;
    }
    
    protected TypeReference parseTypeReference0(int start) {
        TerminalToken token = this.parser.currentToken;
        
        if (token == TokenNamevoid) {
            this.parser.consumeToken();
            return new SingleTypeReference(TypeConstants.VOID, 
                (((long) start) << 32) + this.parser.endPosition());
        }
        
        if (token == TokenNameboolean) {
            this.parser.consumeToken();
            return new SingleTypeReference(TypeConstants.BOOLEAN, 
                (((long) start) << 32) + this.parser.endPosition());
        }
        
        if (token == TokenNamebyte) {
            this.parser.consumeToken();
            return new SingleTypeReference(TypeConstants.BYTE, 
                (((long) start) << 32) + this.parser.endPosition());
        }
        
        if (token == TokenNamechar) {
            this.parser.consumeToken();
            return new SingleTypeReference(TypeConstants.CHAR, 
                (((long) start) << 32) + this.parser.endPosition());
        }
        
        if (token == TokenNameshort) {
            this.parser.consumeToken();
            return new SingleTypeReference(TypeConstants.SHORT, 
                (((long) start) << 32) + this.parser.endPosition());
        }
        
        if (token == TokenNameint) {
            this.parser.consumeToken();
            return new SingleTypeReference(TypeConstants.INT, 
                (((long) start) << 32) + this.parser.endPosition());
        }
        
        if (token == TokenNamelong) {
            this.parser.consumeToken();
            return new SingleTypeReference(TypeConstants.LONG, 
                (((long) start) << 32) + this.parser.endPosition());
        }
        
        if (token == TokenNamefloat) {
            this.parser.consumeToken();
            return new SingleTypeReference(TypeConstants.FLOAT, 
                (((long) start) << 32) + this.parser.endPosition());
        }
        
        if (token == TokenNamedouble) {
            this.parser.consumeToken();
            return new SingleTypeReference(TypeConstants.DOUBLE, 
                (((long) start) << 32) + this.parser.endPosition());
        }
        
        if (token == TokenNameIdentifier || token == TokenNamevoid ||
            this.parser.isModifierToken(token)) {
            return parseClassOrInterfaceType(start);
        }
        
        if (token == TokenNameAT) {
            return parseAnnotatedTypeReference(start);
        }
        
        if (token == TokenNameLPAREN) {
            return parseParenthesizedTypeReference(start);
        }
        
        return null;
    }
    
    protected TypeReference parseClassOrInterfaceType(int start) {
        List<char[]> tokens = new ArrayList<>();
        List<Long> positions = new ArrayList<>();
        List<TypeReference[]> typeArguments = new ArrayList<>();
        List<Annotation[]> annotations = new ArrayList<>();
        
        TypeReference result = parseSegment(tokens, positions, typeArguments, annotations);
        
        while (this.parser.currentToken == TokenNameDOT) {
            this.parser.consumeToken();
            
            List<Annotation> segAnnotations = new ArrayList<>();
            while (this.parser.currentToken == TokenNameAT) {
                ExpressionParser exprParser = new ExpressionParser(this.parser);
                Annotation ann = exprParser.parseAnnotation();
                if (ann != null) {
                    segAnnotations.add(ann);
                }
            }
            
            if (!this.parser.isIdentifier()) {
                this.parser.reportSyntaxError(TokenNameIdentifier);
                break;
            }
            
            int segStart = this.parser.startPosition();
            int segEnd = this.parser.endPosition();
            tokens.add(this.parser.identifier());
            long segPos = (((long) segStart) << 32) + segEnd;
            positions.add(segPos);
            
            TypeReference[] typeArgs = parseTypeArguments();
            typeArguments.add(typeArgs);
            annotations.add(segAnnotations.isEmpty() ? null : segAnnotations.toArray(new Annotation[0]));
        }
        
        if (tokens.size() == 1) {
            if (typeArguments.get(0) == null && annotations.isEmpty()) {
                return new SingleTypeReference(tokens.get(0), positions.get(0));
            }
            
            if (typeArguments.get(0) != null) {
                ParameterizedSingleTypeReference paramRef = new ParameterizedSingleTypeReference(
                    tokens.get(0), typeArguments.get(0), 0, positions.get(0));
                if (!annotations.isEmpty() && annotations.get(0) != null) {
                    paramRef.annotations = new Annotation[][] { annotations.get(0) };
                }
                return paramRef;
            }
            
            SingleTypeReference singleRef = new SingleTypeReference(tokens.get(0), positions.get(0));
            if (!annotations.isEmpty() && annotations.get(0) != null) {
                singleRef.annotations = new Annotation[][] { annotations.get(0) };
            }
            return singleRef;
        }
        
        return buildQualifiedTypeReference(tokens, positions, typeArguments, annotations);
    }
    
    protected TypeReference parseSegment(List<char[]> tokens, List<Long> positions,
            List<TypeReference[]> typeArguments, List<Annotation[]> annotations) {
        
        List<Annotation> leadingAnnotations = new ArrayList<>();
        while (this.parser.currentToken == TokenNameAT) {
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            Annotation ann = exprParser.parseAnnotation();
            if (ann != null) {
                leadingAnnotations.add(ann);
            }
        }
        
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            return null;
        }
        
        int start = this.parser.startPosition();
        int end = this.parser.endPosition();
        tokens.add(this.parser.identifier());
        positions.add((((long) start) << 32) + end);
        
        TypeReference[] typeArgs = parseTypeArguments();
        typeArguments.add(typeArgs);
        annotations.add(leadingAnnotations.isEmpty() ? null : leadingAnnotations.toArray(new Annotation[0]));
        
        return null;
    }
    
    protected TypeReference[] parseTypeArguments() {
        if (this.parser.currentToken != TokenNameLESS) {
            return null;
        }
        
        this.parser.consumeToken();
        
        if (this.parser.currentToken == TokenNameGREATER) {
            this.parser.consumeToken();
            return null;
        }
        
        List<TypeReference> typeArgs = new ArrayList<>();
        
        typeArgs.add(parseTypeArgument());
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            typeArgs.add(parseTypeArgument());
        }
        
        this.parser.match(TokenNameGREATER);
        
        return typeArgs.toArray(new TypeReference[0]);
    }
    
    protected TypeReference parseTypeArgument() {
        if (this.parser.currentToken == TokenNameQUESTION) {
            return parseWildcard();
        }
        return parseTypeReference(true);
    }
    
    protected Wildcard parseWildcard() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        Wildcard wildcard = new Wildcard(Wildcard.UNBOUND);
        
        if (this.parser.currentToken == TokenNameextends) {
            this.parser.consumeToken();
            wildcard.kind = Wildcard.EXTENDS;
            wildcard.bound = parseTypeReference(true);
        } else if (this.parser.currentToken == TokenNamesuper) {
            this.parser.consumeToken();
            wildcard.kind = Wildcard.SUPER;
            wildcard.bound = parseTypeReference(true);
        }
        
        wildcard.sourceStart = start;
        wildcard.sourceEnd = this.parser.endPosition();
        
        return wildcard;
    }
    
    protected TypeReference buildQualifiedTypeReference(List<char[]> tokens, 
            List<Long> positions, List<TypeReference[]> typeArguments, 
            List<Annotation[]> annotations) {
        
        int size = tokens.size();
        char[][] tokensArray = tokens.toArray(new char[0][]);
        long[] positionsArray = new long[size];
        for (int i = 0; i < size; i++) {
            positionsArray[i] = positions.get(i);
        }
        
        boolean hasTypeArguments = false;
        for (TypeReference[] args : typeArguments) {
            if (args != null) {
                hasTypeArguments = true;
                break;
            }
        }
        
        if (hasTypeArguments) {
            TypeReference[][] typeArgsArray = new TypeReference[size][];
            for (int i = 0; i < size; i++) {
                typeArgsArray[i] = typeArguments.get(i);
            }
            
            Annotation[][] annArray = null;
            boolean hasAnnotations = false;
            for (Annotation[] ann : annotations) {
                if (ann != null) {
                    hasAnnotations = true;
                    break;
                }
            }
            if (hasAnnotations) {
                annArray = new Annotation[size][];
                for (int i = 0; i < size; i++) {
                    annArray[i] = annotations.get(i);
                }
            }
            
            ParameterizedQualifiedTypeReference paramRef = new ParameterizedQualifiedTypeReference(
                tokensArray, typeArgsArray, 0, positionsArray);
            if (annArray != null) {
                paramRef.annotations = annArray;
            }
            return paramRef;
        }
        
        return new QualifiedTypeReference(tokensArray, positionsArray);
    }
    
    protected TypeReference parseAnnotatedTypeReference(int start) {
        ExpressionParser exprParser = new ExpressionParser(this.parser);
        Annotation annotation = exprParser.parseAnnotation();
        
        TypeReference baseType = parseTypeReference0(this.parser.startPosition());
        if (baseType != null && annotation != null) {
            if (baseType.annotations == null) {
                baseType.annotations = new Annotation[1][];
            }
            if (baseType.annotations[0] == null) {
                baseType.annotations[0] = new Annotation[] { annotation };
            } else {
                Annotation[] existing = baseType.annotations[0];
                Annotation[] newAnnotations = new Annotation[existing.length + 1];
                System.arraycopy(existing, 0, newAnnotations, 0, existing.length);
                newAnnotations[existing.length] = annotation;
                baseType.annotations[0] = newAnnotations;
            }
        }
        
        return baseType;
    }
    
    protected TypeReference parseParenthesizedTypeReference(int start) {
        this.parser.consumeToken();
        
        TypeReference innerType = parseTypeReference(true);
        
        this.parser.match(TokenNameRPAREN);
        
        int dimensions = parseDimensions();
        
        if (dimensions > 0 && innerType != null) {
            return augmentTypeWithDimensions(innerType, dimensions);
        }
        
        return innerType;
    }
    
    public int parseDimensions() {
        int dimensions = 0;
        
        while (this.parser.currentToken == TokenNameLBRACKET) {
            this.parser.consumeToken();
            this.parser.match(TokenNameRBRACKET);
            dimensions++;
        }
        
        return dimensions;
    }
    
    public TypeReference augmentTypeWithDimensions(TypeReference typeRef, int dimensions) {
        if (dimensions == 0) return typeRef;
        
        if (typeRef instanceof SingleTypeReference) {
            SingleTypeReference singleRef = (SingleTypeReference) typeRef;
            return new ArrayTypeReference(
                singleRef.token, 
                dimensions, 
                (((long) singleRef.sourceStart) << 32) + this.parser.endPosition());
        }
        
        if (typeRef instanceof QualifiedTypeReference) {
            QualifiedTypeReference qualRef = (QualifiedTypeReference) typeRef;
            return new ArrayQualifiedTypeReference(
                qualRef.tokens,
                dimensions,
                qualRef.sourcePositions);
        }
        
        return typeRef;
    }
    
    public TypeReference parseArrayType(TypeReference baseType) {
        int dimensions = parseDimensions();
        if (dimensions > 0) {
            return augmentTypeWithDimensions(baseType, dimensions);
        }
        return baseType;
    }
}
