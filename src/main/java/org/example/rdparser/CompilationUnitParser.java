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
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;

public class CompilationUnitParser {
    
    protected final RDParser parser;
    
    protected ImportReference currentPackage;
    protected final List<ImportReference> imports = new ArrayList<>();
    protected final List<TypeDeclaration> types = new ArrayList<>();
    protected ModuleDeclaration moduleDeclaration;
    
    public CompilationUnitParser(RDParser parser) {
        this.parser = parser;
    }
    
    public void parse() {
        parseOptionalPackageDeclaration();
        parseImportDeclarations();
        parseTypeDeclarations();
        
        buildCompilationUnit();
    }
    
    protected void parseOptionalPackageDeclaration() {
        if (this.parser.currentToken == TokenNamepackage) {
            parsePackageDeclaration();
        }
    }
    
    protected void parsePackageDeclaration() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        char[][] tokens = parseQualifiedName();
        long[] positions = buildPositions(start);
        
        this.parser.match(TokenNameSEMICOLON);
        
        this.currentPackage = new ImportReference(tokens, positions, false, ClassFileConstants.AccDefault);
        this.currentPackage.declarationSourceStart = start;
        this.currentPackage.declarationSourceEnd = this.parser.endPosition();
        
        this.parser.compilationUnit.currentPackage = this.currentPackage;
    }
    
    protected void parseImportDeclarations() {
        while (this.parser.currentToken == TokenNameimport) {
            parseImportDeclaration();
        }
    }
    
    protected void parseImportDeclaration() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        boolean isStatic = this.parser.check(TokenNamestatic);
        boolean onDemand = false;
        
        char[][] tokens = parseQualifiedName();
        
        if (this.parser.currentToken == TokenNameDOT && 
            this.parser.peekToken() == TokenNameMULTIPLY) {
            this.parser.consumeToken();
            this.parser.consumeToken();
            onDemand = true;
        }
        
        this.parser.match(TokenNameSEMICOLON);
        
        ImportReference importRef = new ImportReference(
            tokens, buildPositions(start), onDemand, 
            isStatic ? ClassFileConstants.AccStatic : ClassFileConstants.AccDefault);
        importRef.declarationSourceStart = start;
        importRef.declarationSourceEnd = this.parser.endPosition();
        
        this.imports.add(importRef);
    }
    
    protected void parseTypeDeclarations() {
        while (this.parser.currentToken != TokenNameEOF) {
            if (this.parser.currentToken == TokenNamemodule) {
                parseModuleDeclaration();
                break;
            }
            
            if (isTypeStart()) {
                TypeDeclaration type = parseTypeDeclaration();
                if (type != null) {
                    this.types.add(type);
                }
            } else {
                if (!this.parser.recoverToOrEOF(
                    TokenNameclass, TokenNameinterface, TokenNameenum, 
                    TokenNameRestrictedIdentifierrecord, TokenNameIdentifier, TokenNameAT)) {
                    break;
                }
            }
        }
        
        if (!this.types.isEmpty()) {
            this.parser.compilationUnit.types = this.types.toArray(new TypeDeclaration[0]);
        }
    }
    
    protected boolean isTypeStart() {
        TerminalToken token = this.parser.currentToken;
        return token == TokenNameclass || 
               token == TokenNameinterface ||
               token == TokenNameenum ||
               token == TokenNameRestrictedIdentifierrecord ||
               token == TokenNameIdentifier ||
               token == TokenNameAT ||
               this.parser.isModifierToken(token);
    }
    
    protected TypeDeclaration parseTypeDeclaration() {
        return new TypeDeclarationParser(this.parser).parse();
    }
    
    protected void parseModuleDeclaration() {
        if (!this.parser.isParsingJava17Plus()) {
            this.parser.reportSyntaxError(0, this.parser.startPosition(), this.parser.endPosition());
        }
        this.moduleDeclaration = new ModuleParser(this.parser).parse();
        this.parser.compilationUnit.moduleDeclaration = this.moduleDeclaration;
    }
    
    protected char[][] parseQualifiedName() {
        List<char[]> parts = new ArrayList<>();
        
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            this.parser.synchronize(RecoveryHandler.SYNC_TYPE);
            return CharOperation.NO_CHAR_CHAR;
        }
        
        parts.add(this.parser.identifier());
        
        while (this.parser.currentToken == TokenNameDOT) {
            this.parser.consumeToken();
            if (!this.parser.isIdentifier()) {
                break;
            }
            parts.add(this.parser.identifier());
        }
        
        return parts.toArray(new char[0][]);
    }
    
    protected long[] buildPositions(int start) {
        return new long[] { ((long) start << 32) | this.parser.endPosition() };
    }
    
    protected void buildCompilationUnit() {
        if (!this.imports.isEmpty()) {
            this.parser.compilationUnit.imports = this.imports.toArray(new ImportReference[0]);
        }
    }
}
