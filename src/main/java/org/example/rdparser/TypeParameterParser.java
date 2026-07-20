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

public class TypeParameterParser {
    
    protected final RDParser parser;
    
    public TypeParameterParser(RDParser parser) {
        this.parser = parser;
    }
    
    public TypeParameter[] parse() {
        if (this.parser.currentToken != TokenNameLESS) {
            return null;
        }
        
        this.parser.consumeToken();
        
        List<TypeParameter> typeParameters = new ArrayList<>();
        
        TypeParameter typeParam = parseTypeParameter();
        if (typeParam != null) {
            typeParameters.add(typeParam);
        }
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            typeParam = parseTypeParameter();
            if (typeParam != null) {
                typeParameters.add(typeParam);
            }
        }
        
        this.parser.match(TokenNameGREATER);
        
        return typeParameters.isEmpty() ? null : typeParameters.toArray(new TypeParameter[0]);
    }
    
    protected TypeParameter parseTypeParameter() {
        int start = this.parser.startPosition();
        
        List<Annotation> annotations = new ArrayList<>();
        while (this.parser.currentToken == TokenNameAT) {
            ExpressionParser exprParser = new ExpressionParser(this.parser);
            Annotation annotation = exprParser.parseAnnotation();
            if (annotation != null) {
                annotations.add(annotation);
            }
        }
        
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            return null;
        }
        
        char[] name = this.parser.identifier();
        
        TypeParameter typeParam = new TypeParameter();
        typeParam.name = name;
        typeParam.sourceStart = start;
        typeParam.annotations = annotations.isEmpty() ? null : 
            annotations.toArray(new Annotation[0]);
        
        if (this.parser.currentToken == TokenNameextends) {
            this.parser.consumeToken();
            typeParam.type = parseTypeBound();
        }
        
        typeParam.sourceEnd = this.parser.endPosition();
        
        return typeParam;
    }
    
    protected TypeReference parseTypeBound() {
        TypeReference firstBound = new TypeReferenceParser(this.parser).parseTypeReference();
        
        if (this.parser.currentToken != TokenNameAND) {
            return firstBound;
        }
        
        List<TypeReference> bounds = new ArrayList<>();
        bounds.add(firstBound);
        
        while (this.parser.currentToken == TokenNameAND) {
            this.parser.consumeToken();
            TypeReference bound = new TypeReferenceParser(this.parser).parseTypeReference();
            if (bound != null) {
                bounds.add(bound);
            }
        }
        
        if (bounds.size() == 1) {
            return firstBound;
        }
        
        IntersectionCastTypeReference intersection = new IntersectionCastTypeReference(
            bounds.toArray(new TypeReference[0]));
        intersection.sourceStart = firstBound.sourceStart;
        intersection.sourceEnd = this.parser.endPosition();
        
        return intersection;
    }
}
