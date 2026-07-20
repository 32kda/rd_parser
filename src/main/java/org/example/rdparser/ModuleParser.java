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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;

public class ModuleParser {
    
    protected final RDParser parser;
    
    public ModuleParser(RDParser parser) {
        this.parser = parser;
    }
    
    public ModuleDeclaration parse() {
        int start = this.parser.startPosition();
        
        ModuleDeclaration moduleDecl = ASTHelper.createModuleDeclaration(
            this.parser.compilationUnit.compilationResult, null, null);
        moduleDecl.sourceStart = start;
        moduleDecl.declarationSourceStart = start;
        
        if (this.parser.currentToken == TokenNameopen) {
            moduleDecl.modifiers |= ClassFileConstants.ACC_OPEN;
            this.parser.consumeToken();
        }
        
        this.parser.match(TokenNamemodule);
        
        if (!this.parser.isIdentifier()) {
            this.parser.reportSyntaxError(TokenNameIdentifier);
            return moduleDecl;
        }
        
        char[][] moduleName = parseModuleName();
        char[] flatName = CharOperation.concatWith(moduleName, '.');
        try {
            Field field = ModuleDeclaration.class.getDeclaredField("moduleName");
            field.setAccessible(true);
            field.set(moduleDecl, flatName);
        } catch (Exception e) {
            // ignore
        }
        
        this.parser.match(TokenNameLBRACE);
        moduleDecl.bodyStart = this.parser.startPosition();
        
        parseModuleStatements(moduleDecl);
        
        this.parser.match(TokenNameRBRACE);
        moduleDecl.bodyEnd = this.parser.endPosition();
        moduleDecl.declarationSourceEnd = moduleDecl.bodyEnd;
        
        return moduleDecl;
    }
    
    protected char[][] parseModuleName() {
        List<char[]> parts = new ArrayList<>();
        
        parts.add(this.parser.identifier());
        
        while (this.parser.currentToken == TokenNameDOT) {
            this.parser.consumeToken();
            if (this.parser.isIdentifier()) {
                parts.add(this.parser.identifier());
            }
        }
        
        return parts.toArray(new char[0][]);
    }
    
    protected void parseModuleStatements(ModuleDeclaration moduleDecl) {
        List<ModuleStatement> statements = new ArrayList<>();
        
        while (this.parser.currentToken != TokenNameEOF && 
               this.parser.currentToken != TokenNameRBRACE) {
            
            ModuleStatement stmt = parseModuleStatement();
            if (stmt != null) {
                statements.add(stmt);
            }
            
            if (this.parser.currentToken == TokenNameSEMICOLON) {
                this.parser.consumeToken();
            }
        }
        
        if (!statements.isEmpty()) {
            ASTHelper.setModuleStatements(moduleDecl, statements.toArray(new ModuleStatement[0]));
        }
    }
    
    protected ModuleStatement parseModuleStatement() {
        TerminalToken token = this.parser.currentToken;
        
        switch (token) {
            case TokenNamerequires:
                return parseRequiresStatement();
            case TokenNameexports:
                return parseExportsStatement();
            case TokenNameopens:
                return parseOpensStatement();
            case TokenNameuses:
                return parseUsesStatement();
            case TokenNameprovides:
                return parseProvidesStatement();
            default:
                this.parser.synchronize(RecoveryHandler.SYNC_MEMBER);
                return null;
        }
    }
    
    protected RequiresStatement parseRequiresStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        RequiresStatement requires = ASTHelper.createRequiresStatement(null);
        requires.sourceStart = start;
        
        while (this.parser.currentToken == TokenNametransitive || 
               this.parser.currentToken == TokenNamestatic) {
            if (this.parser.currentToken == TokenNametransitive) {
                requires.modifiers |= ClassFileConstants.ACC_TRANSITIVE;
            } else {
                requires.modifiers |= ClassFileConstants.ACC_STATIC_PHASE;
            }
            this.parser.consumeToken();
        }
        
        requires.module = parseModuleNameReference(start);
        requires.sourceEnd = this.parser.endPosition();
        
        this.parser.match(TokenNameSEMICOLON);
        
        return requires;
    }
    
    protected ExportsStatement parseExportsStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        ImportReference pkgRef = parsePackageNameReference(start);
        ExportsStatement exports = ASTHelper.createExportsStatement(pkgRef);
        exports.sourceStart = start;
        
        if (this.parser.currentToken == TokenNameto) {
            this.parser.consumeToken();
            exports.targets = parseModuleNamesList();
        }
        
        exports.sourceEnd = this.parser.endPosition();
        this.parser.match(TokenNameSEMICOLON);
        
        return exports;
    }
    
    protected OpensStatement parseOpensStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        ImportReference pkgRef = parsePackageNameReference(start);
        OpensStatement opens = ASTHelper.createOpensStatement(pkgRef);
        opens.sourceStart = start;
        
        if (this.parser.currentToken == TokenNameto) {
            this.parser.consumeToken();
            opens.targets = parseModuleNamesList();
        }
        
        opens.sourceEnd = this.parser.endPosition();
        this.parser.match(TokenNameSEMICOLON);
        
        return opens;
    }
    
    protected UsesStatement parseUsesStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        UsesStatement uses = new UsesStatement(null);
        uses.sourceStart = start;
        
        uses.serviceInterface = new TypeReferenceParser(this.parser).parseTypeReference();
        
        uses.sourceEnd = this.parser.endPosition();
        this.parser.match(TokenNameSEMICOLON);
        
        return uses;
    }
    
    protected ProvidesStatement parseProvidesStatement() {
        int start = this.parser.startPosition();
        this.parser.consumeToken();
        
        ProvidesStatement provides = ASTHelper.createProvidesStatement();
        provides.sourceStart = start;
        
        TypeReference serviceRef = new TypeReferenceParser(this.parser).parseTypeReference();
        try {
            Field field = ProvidesStatement.class.getDeclaredField("serviceInterface");
            field.setAccessible(true);
            field.set(provides, serviceRef);
        } catch (Exception e) {
            provides.serviceInterface = serviceRef;
        }
        
        this.parser.match(TokenNamewith);
        
        provides.implementations = parseTypeList();
        
        provides.sourceEnd = this.parser.endPosition();
        this.parser.match(TokenNameSEMICOLON);
        
        return provides;
    }
    
    protected ModuleReference parseModuleNameReference(int start) {
        char[][] moduleName = parseModuleName();
        
        ModuleReference ref = new ModuleReference(moduleName, 
            new long[] { (((long) start) << 32) | this.parser.endPosition() });
        
        return ref;
    }
    
    protected ImportReference parsePackageNameReference(int start) {
        char[][] pkgName = parseModuleName();
        
        ImportReference ref = new ImportReference(pkgName, 
            new long[] { (((long) start) << 32) | this.parser.endPosition() }, false, 0);
        
        return ref;
    }
    
    protected ModuleReference[] parseModuleNamesList() {
        List<ModuleReference> modules = new ArrayList<>();
        
        int start = this.parser.startPosition();
        modules.add(parseModuleNameReference(start));
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            start = this.parser.startPosition();
            modules.add(parseModuleNameReference(start));
        }
        
        return modules.toArray(new ModuleReference[0]);
    }
    
    protected TypeReference[] parseTypeList() {
        List<TypeReference> types = new ArrayList<>();
        
        types.add(new TypeReferenceParser(this.parser).parseTypeReference());
        
        while (this.parser.currentToken == TokenNameCOMMA) {
            this.parser.consumeToken();
            types.add(new TypeReferenceParser(this.parser).parseTypeReference());
        }
        
        return types.toArray(new TypeReference[0]);
    }
}
