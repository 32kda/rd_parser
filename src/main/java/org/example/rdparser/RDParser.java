/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.example.rdparser;

import static org.eclipse.jdt.internal.compiler.parser.TerminalToken.*;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.ReferenceContext;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;
import org.eclipse.jdt.internal.compiler.parser.*;
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;

public class RDParser implements ProblemSeverities {
    
    public static final boolean DEBUG = false;
    
    protected Scanner scanner;
    protected TerminalToken currentToken = TokenNameNotAToken;
    protected ProblemReporter problemReporter;
    protected CompilerOptions options;
    
    protected CompilationUnitDeclaration compilationUnit;
    protected ReferenceContext referenceContext;
    
    protected int lastCheckPoint;
    protected boolean hasError = false;
    protected boolean reportSyntaxErrorIsRequired = true;
    
    protected int modifiers = 0;
    protected int modifiersSourceStart = -1;
    
    protected final List<Annotation> annotations = new ArrayList<>();
    
    protected RecoveryHandler recoveryHandler;
    protected boolean diet = false;
    protected int dietInt = 0;
    
    protected int nestedType = 0;
    protected int nestedMethod = 0;
    
    public RDParser(ProblemReporter problemReporter, boolean optimizeStringLiterals) {
        this.problemReporter = problemReporter;
        this.options = problemReporter.options;
        initializeScanner();
        this.recoveryHandler = new RecoveryHandler(this);
    }
    
    protected void initializeScanner() {
        this.scanner = new Scanner(
            false,
            false,
            false,
            this.options.sourceLevel,
            this.options.complianceLevel,
            this.options.taskTags,
            this.options.taskPriorities,
            this.options.isTaskCaseSensitive,
            this.options.enablePreviewFeatures);
        this.scanner.recordLineSeparator = true;
    }
    
    public void setSource(char[] source) {
        this.scanner.setSource(source);
    }
    
    protected TerminalToken getNextToken() {
        try {
            this.currentToken = this.scanner.getNextToken();
            return this.currentToken;
        } catch (org.eclipse.jdt.core.compiler.InvalidInputException e) {
            this.hasError = true;
            int startPos = this.scanner.startPosition;
            int endPos = this.scanner.currentPosition - 1;
            this.problemReporter.parseErrorNoSuggestion(
                startPos, endPos,
                TokenNameERROR,
                this.scanner.getCurrentTokenSource(),
                e.getMessage());
            return TokenNameERROR;
        }
    }
    
    protected TerminalToken peekToken() {
        int current = this.scanner.currentPosition;
        int start = this.scanner.startPosition;
        TerminalToken saved = this.currentToken;
        try {
            TerminalToken next = this.scanner.getNextToken();
            this.scanner.currentPosition = current;
            this.scanner.startPosition = start;
            this.currentToken = saved;
            return next;
        } catch (org.eclipse.jdt.core.compiler.InvalidInputException e) {
            this.scanner.currentPosition = current;
            this.scanner.startPosition = start;
            this.currentToken = saved;
            return TokenNameERROR;
        }
    }
    
    protected boolean isToken(TerminalToken token) {
        return this.currentToken == token;
    }
    
    protected boolean isIdentifier() {
        return this.currentToken == TokenNameIdentifier;
    }
    
    protected boolean check(TerminalToken expected) {
        if (this.currentToken == expected) {
            getNextToken();
            return true;
        }
        return false;
    }
    
    protected boolean match(TerminalToken expected) {
        if (this.currentToken == expected) {
            getNextToken();
            return true;
        }
        reportSyntaxError(expected);
        return false;
    }
    
    protected boolean matchIdentifier() {
        if (isIdentifier()) {
            getNextToken();
            return true;
        }
        reportSyntaxError(TokenNameIdentifier);
        return false;
    }
    
    protected char[] identifier() {
        if (isIdentifier()) {
            char[] result = this.scanner.getCurrentIdentifierSource();
            getNextToken();
            return result;
        }
        return CharOperation.NO_CHAR;
    }
    
    protected long identifierPosition() {
        return (((long) this.scanner.startPosition) << 32) + (this.scanner.currentPosition - 1);
    }
    
    protected int startPosition() {
        return this.scanner.startPosition;
    }
    
    protected int endPosition() {
        return this.scanner.currentPosition - 1;
    }
    
    protected void consumeToken() {
        getNextToken();
    }
    
    protected void reportSyntaxError(TerminalToken expected) {
        if (!this.reportSyntaxErrorIsRequired) return;
        if (!this.hasError) {
            this.hasError = true;
            int start = this.scanner.startPosition;
            int end = this.scanner.currentPosition - 1;
            String expectedText = expected.name();
            problemReporter().parseErrorNoSuggestion(
                start, end, 
                this.currentToken,
                this.scanner.getCurrentTokenSource(),
                expectedText);
        }
    }
    
    protected void reportSyntaxError(int problemId, int start, int end) {
        if (!this.reportSyntaxErrorIsRequired) return;
        this.hasError = true;
        this.problemReporter.parseErrorNoSuggestion(
            start, end,
            this.currentToken,
            this.scanner.getCurrentTokenSource(),
            "Problem: " + problemId);
    }
    
    protected boolean recoverTo(TerminalToken... tokens) {
        return this.recoveryHandler.recoverTo(tokens);
    }
    
    protected boolean recoverToOrEOF(TerminalToken... tokens) {
        return this.recoveryHandler.recoverToOrEOF(tokens);
    }
    
    protected void synchronize(RecoveryHandler.SyncPoint syncPoint) {
        this.recoveryHandler.synchronize(syncPoint);
    }
    
    protected boolean isModifierToken(TerminalToken token) {
        return token == TokenNamepublic || token == TokenNameprivate || token == TokenNameprotected ||
               token == TokenNamestatic || token == TokenNamefinal || token == TokenNamesynchronized ||
               token == TokenNamevolatile || token == TokenNametransient || token == TokenNamenative ||
               token == TokenNameabstract || token == TokenNamestrictfp || token == TokenNamedefault ||
               token == TokenNamenon_sealed || token == TokenNameRestrictedIdentifiersealed;
    }
    
    protected boolean isTypeStartToken() {
        TerminalToken token = this.currentToken;
        return token == TokenNameclass || token == TokenNameinterface || token == TokenNameenum ||
               token == TokenNameRestrictedIdentifierrecord ||
               token == TokenNameIdentifier || token == TokenNameAT ||
               token == TokenNameboolean || token == TokenNamebyte || token == TokenNamechar ||
               token == TokenNamedouble || token == TokenNamefloat || token == TokenNameint ||
               token == TokenNamelong || token == TokenNameshort || token == TokenNamevoid ||
               isModifierToken(token);
    }
    
    protected boolean isStatementStartToken() {
        TerminalToken token = this.currentToken;
        return token == TokenNameif || token == TokenNamefor || token == TokenNamewhile || token == TokenNamedo ||
               token == TokenNameswitch || token == TokenNametry || token == TokenNamethrow ||
               token == TokenNamereturn || token == TokenNamebreak || token == TokenNamecontinue ||
               token == TokenNamesynchronized || token == TokenNameassert || token == TokenNameLBRACE ||
               token == TokenNameSEMICOLON || token == TokenNameIdentifier || token == TokenNameAT ||
               isModifierToken(token) || isTypeStartToken();
    }
    
    protected boolean isExpressionStartToken() {
        TerminalToken token = this.currentToken;
        return token == TokenNameIdentifier || token == TokenNamenew || token == TokenNamethis || token == TokenNamesuper ||
               token == TokenNamenull || token == TokenNametrue || token == TokenNamefalse ||
               token == TokenNameIntegerLiteral || token == TokenNameLongLiteral ||
               token == TokenNameFloatingPointLiteral || token == TokenNameDoubleLiteral ||
               token == TokenNameCharacterLiteral || token == TokenNameStringLiteral ||
               token == TokenNameTextBlock || token == TokenNameLPAREN || token == TokenNameAT ||
               token == TokenNamePLUS || token == TokenNameMINUS || token == TokenNameNOT || token == TokenNameTWIDDLE ||
               token == TokenNameLESS;
    }
    
    protected boolean isLiteralToken() {
        TerminalToken token = this.currentToken;
        return token == TokenNameIntegerLiteral || token == TokenNameLongLiteral ||
               token == TokenNameFloatingPointLiteral || token == TokenNameDoubleLiteral ||
               token == TokenNameCharacterLiteral || token == TokenNameStringLiteral ||
               token == TokenNameTextBlock || token == TokenNamenull || token == TokenNametrue || token == TokenNamefalse;
    }
    
    public ProblemReporter problemReporter() {
        return this.problemReporter;
    }
    
    public void resetModifiers() {
        this.modifiers = 0;
        this.modifiersSourceStart = -1;
        this.annotations.clear();
    }
    
    protected void checkAndSetModifiers(int flag) {
        if ((this.modifiers & flag) != 0) {
            this.modifiers |= ExtraCompilerModifiers.AccAlternateModifierProblem;
        }
        this.modifiers |= flag;
        if (this.modifiersSourceStart < 0) {
            this.modifiersSourceStart = this.scanner.startPosition;
        }
    }
    
    protected boolean isParsingJava17Plus() {
        return this.options.sourceLevel >= ClassFileConstants.JDK17;
    }
    
    protected boolean isParsingJava21Plus() {
        return this.options.sourceLevel >= ClassFileConstants.JDK21;
    }
    
    protected boolean isParsingJava14Plus() {
        return this.options.sourceLevel >= ClassFileConstants.JDK14;
    }
    
    protected boolean isParsingJava8Plus() {
        return this.options.sourceLevel >= ClassFileConstants.JDK1_8;
    }
    
    public void parse(CompilationUnitDeclaration unit) {
        this.compilationUnit = unit;
        this.referenceContext = unit;
        this.hasError = false;
        
        char[] contents = unit.compilationResult.compilationUnit.getContents();
        setSource(contents);
        getNextToken();
        
        parseCompilationUnit();
    }
    
    protected void parseCompilationUnit() {
        new CompilationUnitParser(this).parse();
    }
    
    public CompilationUnitDeclaration dietParse(ICompilationUnit sourceUnit, CompilationResult compilationResult) {
        this.diet = true;
        CompilationUnitDeclaration unit = new CompilationUnitDeclaration(
            this.problemReporter, compilationResult, 
            sourceUnit.getContents().length);
        unit.compilationResult = compilationResult;
        parse(unit);
        this.diet = false;
        return unit;
    }
    
    public CompilationUnitDeclaration parse(ICompilationUnit sourceUnit, CompilationResult compilationResult) {
        CompilationUnitDeclaration unit = new CompilationUnitDeclaration(
            this.problemReporter, compilationResult, 
            sourceUnit.getContents().length);
        unit.compilationResult = compilationResult;
        parse(unit);
        return unit;
    }
}
