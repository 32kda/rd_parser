package org.example.rdparser;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.eclipse.jdt.internal.compiler.ast.*;

public class ASTHelper {
    
    static {
        try {
            Class.forName("org.eclipse.jdt.internal.compiler.ast.ASTNode");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ASTHelper", e);
        }
    }
    
    public static IntLiteral createIntLiteral(char[] source, int start, int end) {
        return IntLiteral.buildIntLiteral(source, start, end);
    }
    
    public static LongLiteral createLongLiteral(char[] source, int start, int end) {
        return LongLiteral.buildLongLiteral(source, start, end);
    }
    
    public static FloatLiteral createFloatLiteral(char[] source, int start, int end) {
        return new FloatLiteral(source, start, end);
    }
    
    public static DoubleLiteral createDoubleLiteral(char[] source, int start, int end) {
        return new DoubleLiteral(source, start, end);
    }
    
    public static CharLiteral createCharLiteral(char[] source, int start, int end) {
        return new CharLiteral(source, start, end);
    }
    
    public static StringLiteral createStringLiteral(char[] source, int start, int end) {
        return new StringLiteral(source, start, end, 0);
    }
    
    public static TextBlock createTextBlock(char[] source, int start, int end, int lineNumber) {
        return new TextBlock(source, start, end, lineNumber, source.length);
    }
    
    public static ClassLiteralAccess createClassLiteralAccess(int sourceStart, TypeReference typeRef) {
        return new ClassLiteralAccess(sourceStart, typeRef);
    }
    
    public static ReferenceExpression createReferenceExpression(org.eclipse.jdt.internal.compiler.parser.Scanner scanner) {
        return new ReferenceExpression(scanner);
    }
    
    public static LambdaExpression createLambdaExpression(org.eclipse.jdt.internal.compiler.CompilationResult compilationResult, boolean isExpr) {
        return new LambdaExpression(compilationResult, isExpr);
    }
    
    public static void setConstructorReference(ReferenceExpression refExpr) {
        refExpr.bits |= ASTNode.Bit32;
    }
    
    public static ModuleDeclaration createModuleDeclaration(org.eclipse.jdt.internal.compiler.CompilationResult compilationResult, char[][] moduleName, long[] moduleExports) {
        return new ModuleDeclaration(compilationResult, moduleName, moduleExports);
    }
    
    public static RequiresStatement createRequiresStatement(ModuleReference moduleRef) {
        return new RequiresStatement(moduleRef);
    }
    
    public static ExportsStatement createExportsStatement(ImportReference pkg) {
        return new ExportsStatement(pkg);
    }
    
    public static OpensStatement createOpensStatement(ImportReference pkg) {
        return new OpensStatement(pkg);
    }
    
    public static ProvidesStatement createProvidesStatement() {
        return new ProvidesStatement();
    }
    
    public static ForStatement createForStatement(Statement[] inits, Expression condition, Statement[] increments, Statement action, boolean hasInit, int start, int end) {
        return new ForStatement(inits, condition, increments, action, hasInit, start, end);
    }
    
    public static DoStatement createDoStatement(Expression condition, Statement action, int start, int end) {
        return new DoStatement(condition, action, start, end);
    }
    
    public static CaseStatement createCaseStatement(Expression[] caseExprs, int start, int end) {
        return new CaseStatement(caseExprs, start, end);
    }
    
    public static void setCaseExpressions(CaseStatement caseStmt, Expression[] expressions) {
        try {
            Field field = CaseStatement.class.getDeclaredField("expressions");
            field.setAccessible(true);
            field.set(caseStmt, expressions);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set case expressions", e);
        }
    }
    
    public static void setModuleStatements(ModuleDeclaration moduleDecl, ModuleStatement[] statements) {
        try {
            Field field = ModuleDeclaration.class.getDeclaredField("statements");
            field.setAccessible(true);
            field.set(moduleDecl, statements);
        } catch (Exception e) {
            try {
                Field field = ModuleDeclaration.class.getDeclaredField("moduleStatements");
                field.setAccessible(true);
                field.set(moduleDecl, statements);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to set module statements", ex);
            }
        }
    }
    
    public static void setExportPackageName(ExportsStatement exports, ImportReference pkgName) {
        try {
            Field field = ExportsStatement.class.getDeclaredField("sourceName");
            field.setAccessible(true);
            field.set(exports, pkgName);
        } catch (Exception e) {
            try {
                Field field = ExportsStatement.class.getDeclaredField("pkg");
                field.setAccessible(true);
                field.set(exports, pkgName);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to set export package name", ex);
            }
        }
    }
    
    public static void setOpenPackageName(OpensStatement opens, ImportReference pkgName) {
        try {
            Field field = OpensStatement.class.getDeclaredField("sourceName");
            field.setAccessible(true);
            field.set(opens, pkgName);
        } catch (Exception e) {
            try {
                Field field = OpensStatement.class.getDeclaredField("pkg");
                field.setAccessible(true);
                field.set(opens, pkgName);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to set open package name", ex);
            }
        }
    }
}
