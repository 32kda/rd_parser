package org.example.rdparser;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;

public class ASTComparator {

    /** Mask of modifiers that can be explicitly written in source: strip synthetic/context-derived bits */
    private static final int SOURCE_MODIFIER_MASK =
        ClassFileConstants.AccPublic | ClassFileConstants.AccPrivate | ClassFileConstants.AccProtected |
        ClassFileConstants.AccStatic | ClassFileConstants.AccFinal | ClassFileConstants.AccSynchronized |
        ClassFileConstants.AccVolatile | ClassFileConstants.AccTransient | ClassFileConstants.AccNative |
        ClassFileConstants.AccAbstract | ClassFileConstants.AccStrictfp |
        ExtraCompilerModifiers.AccSealed | ExtraCompilerModifiers.AccNonSealed;

    public static class ComparisonResult {
        public final int totalChecks;
        public final int matchCount;
        public final List<String> differences;

        public ComparisonResult(int totalChecks, int matchCount, List<String> differences) {
            this.totalChecks = totalChecks;
            this.matchCount = matchCount;
            this.differences = differences;
        }

        public boolean isIdentical() {
            return differences.isEmpty();
        }

        public double matchRatio() {
            return totalChecks == 0 ? 1.0 : (double) matchCount / totalChecks;
        }

        public void printSummary(String label) {
            System.out.println("  " + label + ": " + matchCount + "/" + totalChecks + " match (" + String.format("%.1f", matchRatio() * 100) + "%)");
            if (!differences.isEmpty()) {
                System.out.println("  Differences:");
                for (String d : differences) {
                    System.out.println("    - " + d);
                }
            }
        }
    }

    public static ComparisonResult compare(CompilationUnitDeclaration oldUnit, CompilationUnitDeclaration newUnit) {
        List<String> diffs = new ArrayList<>();
        int[] counts = new int[2];
        compareCompilationUnits(oldUnit, newUnit, diffs, counts);
        return new ComparisonResult(counts[0] + counts[1], counts[0], diffs);
    }

    private static void compareCompilationUnits(CompilationUnitDeclaration oldUnit, CompilationUnitDeclaration newUnit,
            List<String> diffs, int[] counts) {
        String pkgOld = "";
        if (oldUnit.currentPackage != null) {
            char[][] name = oldUnit.currentPackage.getImportName();
            if (name != null && name.length > 0 && name[0] != null) {
                pkgOld = new String(name[0]);
            }
        }
        String pkgNew = "";
        if (newUnit.currentPackage != null) {
            char[][] name = newUnit.currentPackage.getImportName();
            if (name != null && name.length > 0 && name[0] != null) {
                pkgNew = new String(name[0]);
            }
        }
        if (!pkgOld.equals(pkgNew)) {
            diffs.add("Package mismatch: '" + pkgOld + "' vs '" + pkgNew + "'");
        }

        int importCountOld = oldUnit.imports == null ? 0 : oldUnit.imports.length;
        int importCountNew = newUnit.imports == null ? 0 : newUnit.imports.length;
        if (importCountOld != importCountNew) {
            diffs.add("Import count mismatch: " + importCountOld + " vs " + importCountNew);
        }
        counts[0]++;
        counts[1]++;

        compareTypeArrays(oldUnit.types, newUnit.types, "", diffs, counts);
    }

    private static void compareTypeArrays(TypeDeclaration[] oldTypes, TypeDeclaration[] newTypes,
            String path, List<String> diffs, int[] counts) {
        int oldLen = oldTypes == null ? 0 : oldTypes.length;
        int newLen = newTypes == null ? 0 : newTypes.length;

        if (oldLen != newLen) {
            diffs.add(path + "Type count mismatch: " + oldLen + " vs " + newLen);
        }
        counts[0]++;
        counts[1]++;

        int minLen = Math.min(oldLen, newLen);
        for (int i = 0; i < minLen; i++) {
            compareTypeDeclarations(oldTypes[i], newTypes[i], path + "[" + i + "]", diffs, counts);
        }
    }

    private static void compareTypeDeclarations(TypeDeclaration oldType, TypeDeclaration newType,
            String path, List<String> diffs, int[] counts) {
        String nameOld = oldType.name == null ? "" : new String(oldType.name);
        String nameNew = newType.name == null ? "" : new String(newType.name);
        checkEqual(nameOld, nameNew, path + " name", diffs, counts);

        checkEqual(oldType.modifiers & SOURCE_MODIFIER_MASK, newType.modifiers & SOURCE_MODIFIER_MASK, path + " modifiers", diffs, counts);

        String superOld = oldType.superclass == null ? "" : typeRefToString(oldType.superclass);
        String superNew = newType.superclass == null ? "" : typeRefToString(newType.superclass);
        checkEqual(superOld, superNew, path + " superclass", diffs, counts);

        int ifaceOld = oldType.superInterfaces == null ? 0 : oldType.superInterfaces.length;
        int ifaceNew = newType.superInterfaces == null ? 0 : newType.superInterfaces.length;
        checkEqual(ifaceOld, ifaceNew, path + " superInterfaces count", diffs, counts);

        int typeParamOld = oldType.typeParameters == null ? 0 : oldType.typeParameters.length;
        int typeParamNew = newType.typeParameters == null ? 0 : newType.typeParameters.length;
        checkEqual(typeParamOld, typeParamNew, path + " typeParameters count", diffs, counts);

        compareFieldArrays(oldType.fields, newType.fields, path + "(" + nameOld + ")", diffs, counts);
        compareMethodArrays(oldType.methods, newType.methods, path + "(" + nameOld + ")", diffs, counts);
        compareTypeArrays(oldType.memberTypes, newType.memberTypes, path + "(" + nameOld + ")", diffs, counts);
    }

    private static void compareFieldArrays(FieldDeclaration[] oldFields, FieldDeclaration[] newFields,
            String path, List<String> diffs, int[] counts) {
        int oldLen = oldFields == null ? 0 : oldFields.length;
        int newLen = newFields == null ? 0 : newFields.length;

        if (oldLen != newLen) {
            diffs.add(path + " Field count mismatch: " + oldLen + " vs " + newLen);
        }
        counts[0]++;
        counts[1]++;

        int minLen = Math.min(oldLen, newLen);
        for (int i = 0; i < minLen; i++) {
            compareFieldDeclarations(oldFields[i], newFields[i], path + ".field[" + i + "]", diffs, counts);
        }
    }

    private static void compareFieldDeclarations(FieldDeclaration oldField, FieldDeclaration newField,
            String path, List<String> diffs, int[] counts) {
        String nameOld = oldField.name == null ? "" : new String(oldField.name);
        String nameNew = newField.name == null ? "" : new String(newField.name);
        checkEqual(nameOld, nameNew, path + " name", diffs, counts);

        String typeOld = oldField.type == null ? "" : typeRefToString(oldField.type);
        String typeNew = newField.type == null ? "" : typeRefToString(newField.type);
        checkEqual(typeOld, typeNew, path + " type", diffs, counts);

        checkEqual(oldField.modifiers, newField.modifiers, path + " modifiers", diffs, counts);
    }

    private static void compareMethodArrays(AbstractMethodDeclaration[] oldMethods, AbstractMethodDeclaration[] newMethods,
            String path, List<String> diffs, int[] counts) {
        AbstractMethodDeclaration[] oldFiltered = filterSyntheticMethods(oldMethods);
        AbstractMethodDeclaration[] newFiltered = newMethods;

        int oldLen = oldFiltered == null ? 0 : oldFiltered.length;
        int newLen = newFiltered == null ? 0 : newFiltered.length;

        if (oldLen != newLen) {
            int oldRaw = oldMethods == null ? 0 : oldMethods.length;
            diffs.add(path + " Method count mismatch: " + oldLen + " (raw " + oldRaw + ") vs " + newLen);
        }
        counts[0]++;
        counts[1]++;

        int minLen = Math.min(oldLen, newLen);
        for (int i = 0; i < minLen; i++) {
            compareAbstractMethods(oldFiltered[i], newFiltered[i], path + ".method[" + i + "]", diffs, counts);
        }
    }

    private static AbstractMethodDeclaration[] filterSyntheticMethods(AbstractMethodDeclaration[] methods) {
        if (methods == null) return null;
        List<AbstractMethodDeclaration> filtered = new ArrayList<>();
        for (AbstractMethodDeclaration m : methods) {
            String name = m.selector == null ? "" : new String(m.selector);
            if ("<clinit>".equals(name)) continue;
            if (m.isDefaultConstructor()) continue;
            filtered.add(m);
        }
        return filtered.isEmpty() ? null : filtered.toArray(new AbstractMethodDeclaration[0]);
    }

    private static void compareAbstractMethods(AbstractMethodDeclaration oldMethod, AbstractMethodDeclaration newMethod,
            String path, List<String> diffs, int[] counts) {
        String nameOld = oldMethod.selector == null ? "" : new String(oldMethod.selector);
        String nameNew = newMethod.selector == null ? "" : new String(newMethod.selector);
        checkEqual(nameOld, nameNew, path + " name", diffs, counts);

        checkEqual(oldMethod.modifiers & SOURCE_MODIFIER_MASK, newMethod.modifiers & SOURCE_MODIFIER_MASK, path + " modifiers", diffs, counts);

        int argCountOld = oldMethod.arguments == null ? 0 : oldMethod.arguments.length;
        int argCountNew = newMethod.arguments == null ? 0 : newMethod.arguments.length;
        checkEqual(argCountOld, argCountNew, path + " arg count", diffs, counts);

        if (oldMethod instanceof MethodDeclaration && newMethod instanceof MethodDeclaration) {
            MethodDeclaration oldMd = (MethodDeclaration) oldMethod;
            MethodDeclaration newMd = (MethodDeclaration) newMethod;
            String retOld = oldMd.returnType == null ? "" : typeRefToString(oldMd.returnType);
            String retNew = newMd.returnType == null ? "" : typeRefToString(newMd.returnType);
            checkEqual(retOld, retNew, path + " returnType", diffs, counts);

            int stmtOld = oldMd.statements == null ? 0 : oldMd.statements.length;
            int stmtNew = newMd.statements == null ? 0 : newMd.statements.length;
            checkEqual(stmtOld, stmtNew, path + " statement count", diffs, counts);

            if (stmtOld > 0 && stmtNew > 0) {
                compareStatements(oldMd.statements, newMd.statements, path, diffs, counts);
            }
        }
    }

    private static void compareStatements(Statement[] oldStmts, Statement[] newStmts,
            String path, List<String> diffs, int[] counts) {
        int minLen = Math.min(oldStmts.length, newStmts.length);
        for (int i = 0; i < minLen; i++) {
            Statement oldStmt = oldStmts[i];
            Statement newStmt = newStmts[i];
            String oldType = oldStmt == null ? "null" : oldStmt.getClass().getSimpleName();
            String newType = newStmt == null ? "null" : newStmt.getClass().getSimpleName();
            checkEqual(oldType, newType, path + " stmt[" + i + "] type", diffs, counts);

            if (oldStmt instanceof LocalDeclaration && newStmt instanceof LocalDeclaration) {
                LocalDeclaration oldLocal = (LocalDeclaration) oldStmt;
                LocalDeclaration newLocal = (LocalDeclaration) newStmt;
                String nOld = oldLocal.name == null ? "" : new String(oldLocal.name);
                String nNew = newLocal.name == null ? "" : new String(newLocal.name);
                checkEqual(nOld, nNew, path + " stmt[" + i + "] local name", diffs, counts);

                String tOld = oldLocal.type == null ? "" : typeRefToString(oldLocal.type);
                String tNew = newLocal.type == null ? "" : typeRefToString(newLocal.type);
                checkEqual(tOld, tNew, path + " stmt[" + i + "] local type", diffs, counts);
            }

            if (oldStmt instanceof MessageSend && newStmt instanceof MessageSend) {
                MessageSend oldMsg = (MessageSend) oldStmt;
                MessageSend newMsg = (MessageSend) newStmt;
                receiversEqual(oldMsg.receiver, newMsg.receiver, path + " stmt[" + i + "] msgSend receiver", diffs, counts);
                String selOld = oldMsg.selector == null ? "" : new String(oldMsg.selector);
                String selNew = newMsg.selector == null ? "" : new String(newMsg.selector);
                checkEqual(selOld, selNew, path + " stmt[" + i + "] msgSend selector", diffs, counts);
            }

            if (oldStmt instanceof Expression && newStmt instanceof Expression) {
                compareExpressions((Expression) oldStmt, (Expression) newStmt, path + " stmt[" + i + "]", diffs, counts);
            }
        }
    }

    private static void compareExpressions(Expression oldExpr, Expression newExpr,
            String path, List<String> diffs, int[] counts) {
        String oldType = oldExpr == null ? "null" : oldExpr.getClass().getSimpleName();
        String newType = newExpr == null ? "null" : newExpr.getClass().getSimpleName();
        checkEqual(oldType, newType, path + " expr type", diffs, counts);
    }

    private static boolean receiversEqual(Expression oldRcvr, Expression newRcvr, String path,
            List<String> diffs, int[] counts) {
        if (oldRcvr == null && newRcvr == null) { counts[0]++; counts[1]++; return true; }
        if (oldRcvr == null || newRcvr == null) {
            diffs.add(path + " one receiver null");
            counts[0]++; counts[1]++;
            return false;
        }
        String oldType = oldRcvr.getClass().getSimpleName();
        String newType = newRcvr.getClass().getSimpleName();
        checkEqual(oldType, newType, path + " class", diffs, counts);

        if (oldRcvr instanceof SingleNameReference && newRcvr instanceof SingleNameReference) {
            String nOld = new String(((SingleNameReference) oldRcvr).token);
            String nNew = new String(((SingleNameReference) newRcvr).token);
            checkEqual(nOld, nNew, path + " name", diffs, counts);
        }
        return true;
    }

    private static void checkEqual(Object oldVal, Object newVal, String label, List<String> diffs, int[] counts) {
        counts[0]++;
        counts[1]++;
        boolean eq = oldVal == null ? newVal == null : oldVal.equals(newVal);
        if (!eq) {
            diffs.add(label + ": '" + oldVal + "' vs '" + newVal + "'");
        }
    }

    private static String typeRefToString(TypeReference ref) {
        if (ref == null) return "";
        if (ref instanceof SingleTypeReference) {
            char[] token = ((SingleTypeReference) ref).token;
            return token == null ? "" : new String(token);
        }
        if (ref instanceof QualifiedTypeReference) {
            QualifiedTypeReference qtr = (QualifiedTypeReference) ref;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < qtr.tokens.length; i++) {
                if (i > 0) sb.append('.');
                if (qtr.tokens[i] != null) sb.append(qtr.tokens[i]);
            }
            return sb.toString();
        }
        if (ref instanceof ArrayTypeReference) {
            ArrayTypeReference atr = (ArrayTypeReference) ref;
            char[] token = atr.token;
            String base = token == null ? "" : new String(token);
            return base + "[]".repeat(atr.dimensions);
        }
        if (ref instanceof ParameterizedSingleTypeReference) {
            ParameterizedSingleTypeReference psr = (ParameterizedSingleTypeReference) ref;
            char[] token = psr.token;
            return (token == null ? "" : new String(token)) + "<...>";
        }
        if (ref instanceof ArrayQualifiedTypeReference) {
            ArrayQualifiedTypeReference aqtr = (ArrayQualifiedTypeReference) ref;
            StringBuilder sb = new StringBuilder();
            for (char[] t : aqtr.tokens) {
                if (sb.length() > 0) sb.append('.');
                if (t != null) sb.append(t);
            }
            return sb.toString();
        }
        if (ref instanceof Wildcard) {
            Wildcard wc = (Wildcard) ref;
            switch (wc.kind) {
                case Wildcard.EXTENDS: return "? extends " + typeRefToString(wc.bound);
                case Wildcard.SUPER: return "? super " + typeRefToString(wc.bound);
                default: return "?";
            }
        }
        return ref.getClass().getSimpleName();
    }

    public static String dumpAST(CompilationUnitDeclaration unit) {
        StringBuilder sb = new StringBuilder();
        dumpNode(unit, "", sb);
        return sb.toString();
    }

    private static void dumpNode(ASTNode node, String indent, StringBuilder sb) {
        if (node == null) {
            sb.append(indent).append("null\n");
            return;
        }

        String className = node.getClass().getSimpleName();
        sb.append(indent).append(className);

        if (node instanceof TypeDeclaration) {
            TypeDeclaration td = (TypeDeclaration) node;
            sb.append(" name=").append(td.name == null ? "null" : new String(td.name));
        } else if (node instanceof FieldDeclaration) {
            FieldDeclaration fd = (FieldDeclaration) node;
            sb.append(" name=").append(new String(fd.name));
            if (fd.type != null) sb.append(" type=").append(typeRefToString(fd.type));
        } else if (node instanceof AbstractMethodDeclaration) {
            AbstractMethodDeclaration amd = (AbstractMethodDeclaration) node;
            sb.append(" name=").append(amd.selector == null ? "null" : new String(amd.selector));
            if (amd instanceof MethodDeclaration && ((MethodDeclaration) amd).returnType != null) {
                sb.append(" return=").append(typeRefToString(((MethodDeclaration) amd).returnType));
            }
        } else if (node instanceof MessageSend) {
            MessageSend ms = (MessageSend) node;
            sb.append(" selector=").append(ms.selector == null ? "null" : new String(ms.selector));
        } else if (node instanceof SingleNameReference) {
            SingleNameReference snr = (SingleNameReference) node;
            sb.append(" token=").append(snr.token == null ? "null" : new String(snr.token));
        } else if (node instanceof QualifiedNameReference) {
            QualifiedNameReference qnr = (QualifiedNameReference) node;
            sb.append(" tokens=");
            if (qnr.tokens != null) {
                for (int i = 0; i < qnr.tokens.length; i++) {
                    if (i > 0) sb.append('.');
                    sb.append(qnr.tokens[i] == null ? "null" : new String(qnr.tokens[i]));
                }
            }
        } else if (node instanceof StringLiteral) {
            sb.append(" value=\"").append(new String(((StringLiteral) node).source())).append("\"");
        } else if (node instanceof NumberLiteral) {
            sb.append(" value=").append(new String(((NumberLiteral) node).source()));
        } else if (node instanceof FalseLiteral) {
            sb.append(" false");
        } else if (node instanceof TrueLiteral) {
            sb.append(" true");
        } else if (node instanceof NullLiteral) {
            sb.append(" null");
        }

        sb.append("\n");

        if (node instanceof CompilationUnitDeclaration) {
            CompilationUnitDeclaration cud = (CompilationUnitDeclaration) node;
            if (cud.types != null) {
                for (TypeDeclaration t : cud.types) {
                    if (t != null) dumpNode(t, indent + "  ", sb);
                }
            }
        } else if (node instanceof TypeDeclaration) {
            TypeDeclaration td = (TypeDeclaration) node;
            if (td.fields != null) {
                for (FieldDeclaration f : td.fields) {
                    if (f != null) dumpNode(f, indent + "  ", sb);
                }
            }
            if (td.methods != null) {
                for (AbstractMethodDeclaration m : td.methods) {
                    if (m != null) dumpNode(m, indent + "  ", sb);
                }
            }
            if (td.memberTypes != null) {
                for (TypeDeclaration t : td.memberTypes) {
                    if (t != null) dumpNode(t, indent + "  ", sb);
                }
            }
        } else if (node instanceof AbstractMethodDeclaration) {
            AbstractMethodDeclaration amd = (AbstractMethodDeclaration) node;
            if (amd.arguments != null) {
                for (Argument arg : amd.arguments) {
                    if (arg != null) dumpNode(arg, indent + "  ", sb);
                }
            }
            if (amd.statements != null) {
                for (Statement s : amd.statements) {
                    if (s != null) dumpNode(s, indent + "  ", sb);
                }
            }
        } else if (node instanceof LocalDeclaration) {
            LocalDeclaration ld = (LocalDeclaration) node;
            sb.append(indent).append("  type=").append(typeRefToString(ld.type))
              .append(" name=").append(new String(ld.name)).append("\n");
            if (ld.initialization != null) {
                dumpNode(ld.initialization, indent + "    ", sb);
            }
        }
    }
}
