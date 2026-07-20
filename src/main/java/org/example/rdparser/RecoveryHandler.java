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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;

public class RecoveryHandler {
    
    protected final RDParser parser;
    
    protected int lastErrorPosition = -1;
    protected int errorCount = 0;
    protected int maxErrors = 100;
    
    public RecoveryHandler(RDParser parser) {
        this.parser = parser;
    }
    
    public boolean recoverTo(TerminalToken... tokens) {
        if (tokens.length == 0) return false;
        
        Set<TerminalToken> tokenSet = new HashSet<>(Arrays.asList(tokens));
        
        while (this.parser.currentToken != TokenNameEOF) {
            if (tokenSet.contains(this.parser.currentToken)) {
                return true;
            }
            this.parser.consumeToken();
        }
        return false;
    }
    
    public boolean recoverToOrEOF(TerminalToken... tokens) {
        if (this.parser.currentToken == TokenNameEOF) {
            return true;
        }
        return recoverTo(tokens);
    }
    
    public void synchronize(SyncPoint syncPoint) {
        TerminalToken[] syncTokens = syncPoint.getSyncTokens();
        int[] syncPairs = syncPoint.getBracketPairs();
        
        int bracketBalance = 0;
        int parenBalance = 0;
        int braceBalance = 0;
        
        while (this.parser.currentToken != TokenNameEOF) {
            TerminalToken token = this.parser.currentToken;
            
            for (TerminalToken sync : syncTokens) {
                if (token == sync && bracketBalance == 0 && parenBalance == 0 && braceBalance == 0) {
                    return;
                }
            }
            
            if (syncPairs != null) {
                for (int i = 0; i < syncPairs.length; i += 2) {
                    if (token.tokenNumber() == syncPairs[i]) {
                        if (i == 0) braceBalance++;
                        else if (i == 2) bracketBalance++;
                        else if (i == 4) parenBalance++;
                    }
                    if (token.tokenNumber() == syncPairs[i + 1]) {
                        if (i == 0) braceBalance--;
                        else if (i == 2) bracketBalance--;
                        else if (i == 4) parenBalance--;
                    }
                }
            }
            
            switch (token) {
                case TokenNameLBRACE: braceBalance++; break;
                case TokenNameRBRACE: 
                    braceBalance--;
                    if (braceBalance < 0) return;
                    break;
                case TokenNameLPAREN: parenBalance++; break;
                case TokenNameRPAREN: 
                    parenBalance--;
                    if (parenBalance < 0 && braceBalance == 0) return;
                    break;
                case TokenNameLBRACKET: bracketBalance++; break;
                case TokenNameRBRACKET: bracketBalance--; break;
                default: break;
            }
            
            this.parser.consumeToken();
        }
    }
    
    public boolean shouldReportError(int position) {
        if (position <= this.lastErrorPosition) {
            return false;
        }
        if (this.errorCount >= this.maxErrors) {
            return false;
        }
        this.lastErrorPosition = position;
        this.errorCount++;
        return true;
    }
    
    public void reset() {
        this.lastErrorPosition = -1;
        this.errorCount = 0;
    }
    
    public static class SyncPoint {
        private final TerminalToken[] syncTokens;
        private final int[] bracketPairs;
        
        public SyncPoint(TerminalToken... syncTokens) {
            this.syncTokens = syncTokens;
            this.bracketPairs = new int[] {
                TokenNameLBRACE.tokenNumber(), TokenNameRBRACE.tokenNumber(),
                TokenNameLBRACKET.tokenNumber(), TokenNameRBRACKET.tokenNumber(),
                TokenNameLPAREN.tokenNumber(), TokenNameRPAREN.tokenNumber()
            };
        }
        
        public TerminalToken[] getSyncTokens() {
            return this.syncTokens;
        }
        
        public int[] getBracketPairs() {
            return this.bracketPairs;
        }
    }
    
    public static final SyncPoint SYNC_STATEMENT = new SyncPoint(
        TokenNameSEMICOLON, TokenNameRBRACE, TokenNameEOF
    );
    
    public static final SyncPoint SYNC_BLOCK = new SyncPoint(
        TokenNameRBRACE, TokenNameEOF
    );
    
    public static final SyncPoint SYNC_CLASS_BODY = new SyncPoint(
        TokenNameRBRACE, TokenNamepublic, TokenNameprivate, TokenNameprotected,
        TokenNamestatic, TokenNamefinal, TokenNameabstract, TokenNamenative,
        TokenNamesynchronized, TokenNamevolatile, TokenNametransient,
        TokenNameclass, TokenNameinterface, TokenNameenum, TokenNameRestrictedIdentifierrecord,
        TokenNamevoid, TokenNameIdentifier, TokenNameLBRACE, TokenNameAT
    );
    
    public static final SyncPoint SYNC_METHOD_BODY = new SyncPoint(
        TokenNameRBRACE, TokenNameEOF
    );
    
    public static final SyncPoint SYNC_EXPRESSION = new SyncPoint(
        TokenNameSEMICOLON, TokenNameCOMMA, TokenNameRPAREN, 
        TokenNameRBRACKET, TokenNameRBRACE, TokenNameEOF
    );
    
    public static final SyncPoint SYNC_TYPE = new SyncPoint(
        TokenNameLBRACE, TokenNameSEMICOLON, TokenNameEOF,
        TokenNameextends, TokenNameimplements, TokenNameRestrictedIdentifierpermits
    );
    
    public static final SyncPoint SYNC_MEMBER = new SyncPoint(
        TokenNameRBRACE, TokenNameSEMICOLON, TokenNameEOF,
        TokenNamepublic, TokenNameprivate, TokenNameprotected,
        TokenNamestatic, TokenNamefinal, TokenNameabstract, TokenNamenative,
        TokenNamesynchronized, TokenNamevolatile, TokenNametransient,
        TokenNameclass, TokenNameinterface, TokenNameenum, TokenNameRestrictedIdentifierrecord,
        TokenNamevoid, TokenNameIdentifier, TokenNameAT
    );
}
