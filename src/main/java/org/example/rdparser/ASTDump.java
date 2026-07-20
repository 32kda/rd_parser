package org.example.rdparser;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;

public class ASTDump {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: ASTDump <file>");
            return;
        }
        Class.forName("org.example.rdparser.ASTHelper");

        char[] source = new String(Files.readAllBytes(new File(args[0]).toPath())).toCharArray();

        CompilerOptions options = new CompilerOptions();
        options.sourceLevel = ClassFileConstants.JDK11;
        options.complianceLevel = ClassFileConstants.JDK11;
        IErrorHandlingPolicy policy = DefaultErrorHandlingPolicies.proceedWithAllProblems();

        ICompilationUnit unit = new RDParserTest.SimpleCompilationUnit(source, args[0]);

        Constructor<CompilationResult> crConstructor = CompilationResult.class.getDeclaredConstructor(
            ICompilationUnit.class, int.class, int.class, int.class);
        crConstructor.setAccessible(true);

        // Old Parser
        CompilationResult oldResult = crConstructor.newInstance(unit, 1, 1, 0);
        ProblemReporter oldReporter = new ProblemReporter(policy, options, new DefaultProblemFactory());
        Parser oldParser = new Parser(oldReporter, false);
        CompilationUnitDeclaration oldUnit = oldParser.parse(unit, oldResult);
        System.out.println("=== OLD PARSER AST ===");
        System.out.println(ASTComparator.dumpAST(oldUnit));
        System.out.println("Old unit types length: " + (oldUnit.types == null ? 0 : oldUnit.types.length));
        if (oldUnit.types != null && oldUnit.types.length > 0) {
            System.out.println("Old first type name: " + (oldUnit.types[0].name == null ? "null" : new String(oldUnit.types[0].name)));
        }

        // New Parser
        CompilationResult newResult = crConstructor.newInstance(unit, 1, 1, 0);
        ProblemReporter newReporter = new ProblemReporter(policy, options, new DefaultProblemFactory());
        RDParser newParser = new RDParser(newReporter, false);
        CompilationUnitDeclaration newUnit = newParser.parse(unit, newResult);
        System.out.println("=== NEW PARSER AST ===");
        System.out.println(ASTComparator.dumpAST(newUnit));
        System.out.println("New unit types length: " + (newUnit.types == null ? 0 : newUnit.types.length));
        if (newUnit.types != null && newUnit.types.length > 0) {
            System.out.println("New first type name: " + (newUnit.types[0].name == null ? "null" : new String(newUnit.types[0].name)));
        }
    }
}
