package org.example.rdparser;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;

public class DebugParser {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: DebugParser <file>");
            return;
        }
        Class.forName("org.example.rdparser.ASTHelper");
        
        char[] source = new String(Files.readAllBytes(Paths.get(args[0]))).toCharArray();
        
        CompilerOptions options = new CompilerOptions();
        options.sourceLevel = ClassFileConstants.JDK11;
        options.complianceLevel = ClassFileConstants.JDK11;
        
        IErrorHandlingPolicy policy = new IErrorHandlingPolicy() {
            @Override public boolean stopOnFirstError() { return false; }
            @Override public boolean proceedOnErrors() { return true; }
            public boolean ignoreDuplicateProblems() { return false; }
            public boolean ignoreAllProblems() { return false; }
            @Override public boolean ignoreAllErrors() { return false; }
        };
        ProblemReporter problemReporter = new ProblemReporter(
            policy, options, new DefaultProblemFactory());
        
        RDParser parser = new RDParser(problemReporter, false);
        
        ICompilationUnit unit = new RDParserTest.SimpleCompilationUnit(source, args[0]);
        
        Constructor<CompilationResult> crConstructor = CompilationResult.class.getDeclaredConstructor(
            ICompilationUnit.class, int.class, int.class, int.class);
        crConstructor.setAccessible(true);
        CompilationResult compilationResult = crConstructor.newInstance(unit, 1, 1, 0);
        
        try {
            parser.parse(unit, compilationResult);
        } catch (Exception e) {
            System.out.println("\n=== EXCEPTION ===");
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getName();
            System.out.println(msg);
            for (StackTraceElement ste : e.getStackTrace()) {
                if (ste.getClassName().startsWith("org.example.")) {
                    System.out.println("  at " + ste.getClassName() + "." + ste.getMethodName() + "(" + ste.getFileName() + ":" + ste.getLineNumber() + ")");
                }
            }
        }
        
        IProblem[] problems = compilationResult.getProblems();
        if (problems != null) {
            for (IProblem p : problems) {
                if (p.isError()) {
                    System.out.println("ERROR [" + p.getSourceStart() + "-" + p.getSourceEnd() + "]: " + p.getMessage());
                }
            }
        }
    }
}
