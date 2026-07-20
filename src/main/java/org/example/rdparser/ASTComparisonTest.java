package org.example.rdparser;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.compiler.IProblem;
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

public class ASTComparisonTest {

    private static int total = 0;
    private static int totalParseable = 0;
    private static int identicalCount = 0;
    private static List<FileResult> results = new ArrayList<>();

    static class FileResult {
        String path;
        boolean oldParsed;
        boolean newParsed;
        ASTComparator.ComparisonResult comparison;
        String error;
        Parser oldParser;

        void print() {
            System.out.print("[" + total + "] " + path + "... ");
            if (!oldParsed) {
                System.out.println("OLD PARSE FAILED");
                if (error != null) System.out.println("    Error: " + error);
                return;
            }
            if (!newParsed) {
                System.out.println("NEW PARSE FAILED");
                if (error != null) System.out.println("    Error: " + error);
                return;
            }
            if (comparison == null) {
                System.out.println("COMPARISON FAILED");
                if (error != null) System.out.println("    Error: " + error);
                return;
            }
            if (comparison.isIdentical()) {
                identicalCount++;
                System.out.println("IDENTICAL (" + comparison.totalChecks + " checks)");
            } else {
                System.out.println("DIFFERS (" + comparison.matchCount + "/" + comparison.totalChecks
                    + " = " + String.format("%.1f", comparison.matchRatio() * 100) + "%)");
                for (String d : comparison.differences) {
                    System.out.println("    - " + d);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: ASTComparisonTest <java-file-or-directory>");
            return;
        }

        Class.forName("org.example.rdparser.ASTHelper");

        File fileOrDir = new File(args[0]);
        if (fileOrDir.isDirectory()) {
            List<File> javaFiles = new ArrayList<>();
            collectJavaFiles(fileOrDir, javaFiles);
            System.out.println("Found " + javaFiles.size() + " Java files\n");
            for (File f : javaFiles) {
                testFile(f);
            }
        } else {
            testFile(fileOrDir);
        }

        System.out.println("\n=== Summary ===");
        System.out.println("Total: " + total);
        System.out.println("Parseable by both: " + totalParseable);
        System.out.println("Identical ASTs: " + identicalCount);
        System.out.println("Different ASTs: " + (totalParseable - identicalCount));

        if (!results.isEmpty()) {
            System.out.println("\n=== Per-file results ===");
            for (FileResult r : results) {
                System.out.println("  " + r.path + ": "
                    + (r.comparison != null ? (r.comparison.isIdentical() ? "IDENTICAL" : "DIFFERS " + r.comparison.matchRatio() * 100 + "%") : "PARSE ERROR"));
            }
        }
    }

    private static void testFile(File file) {
        total++;
        FileResult fr = new FileResult();
        fr.path = getRelativePath(file);
        results.add(fr);

        try {
            char[] source = new String(Files.readAllBytes(file.toPath())).toCharArray();

            CompilerOptions options = new CompilerOptions();
            options.sourceLevel = ClassFileConstants.JDK11;
            options.complianceLevel = ClassFileConstants.JDK11;

            IErrorHandlingPolicy policy = DefaultErrorHandlingPolicies.proceedWithAllProblems();

            ICompilationUnit unit = new RDParserTest.SimpleCompilationUnit(source, file.getName());

            Constructor<CompilationResult> crConstructor = CompilationResult.class.getDeclaredConstructor(
                ICompilationUnit.class, int.class, int.class, int.class);
            crConstructor.setAccessible(true);
            CompilationResult oldResult = crConstructor.newInstance(unit, 1, 1, 0);

            ProblemReporter oldReporter = new ProblemReporter(policy, options, new DefaultProblemFactory());
            Parser oldParser = new Parser(oldReporter, false);
            fr.oldParser = oldParser;

            CompilationUnitDeclaration oldUnit;
            try {
                oldUnit = oldParser.parse(unit, oldResult);
            } catch (Exception e) {
                fr.oldParsed = false;
                fr.newParsed = false;
                fr.error = "Old parser error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                fr.print();
                return;
            }

            boolean oldHasErrors = hasErrors(oldResult);
            if (oldHasErrors) {
                fr.oldParsed = false;
                fr.newParsed = false;
                fr.error = "Old parser reported errors";
                fr.print();
                return;
            }

            CompilationResult newResult = crConstructor.newInstance(unit, 1, 1, 0);
            ProblemReporter newReporter = new ProblemReporter(policy, options, new DefaultProblemFactory());
            RDParser newParser = new RDParser(newReporter, false);

            CompilationUnitDeclaration newUnit;
            try {
                newUnit = newParser.parse(unit, newResult);
            } catch (Exception e) {
                fr.oldParsed = true;
                fr.newParsed = false;
                fr.error = "New parser error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                fr.print();
                return;
            }

            boolean newHasErrors = hasErrors(newResult);
            if (newHasErrors) {
                fr.oldParsed = true;
                fr.newParsed = false;
                fr.error = "New parser reported errors";
                fr.print();
                return;
            }

            fr.oldParsed = true;
            fr.newParsed = true;
            totalParseable++;

            try {
                fr.comparison = ASTComparator.compare(oldUnit, newUnit);
            } catch (Exception e) {
                fr.error = "Comparison error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                fr.print();
                return;
            }
            fr.print();

        } catch (Exception e) {
            fr.error = "Unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            fr.print();
        }
    }

    private static boolean hasErrors(CompilationResult result) {
        IProblem[] problems = result.getProblems();
        if (problems != null) {
            for (IProblem p : problems) {
                if (p.isError()) return true;
            }
        }
        return false;
    }

    private static String getRelativePath(File file) {
        String path = file.getAbsolutePath();
        String[] parts = path.split("[\\\\/]");
        if (parts.length > 4) {
            StringBuilder sb = new StringBuilder();
            for (int i = parts.length - 4; i < parts.length; i++) {
                if (sb.length() > 0) sb.append("/");
                sb.append(parts[i]);
            }
            return sb.toString();
        }
        return file.getName();
    }

    private static void collectJavaFiles(File dir, List<File> files) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectJavaFiles(entry, files);
            } else if (entry.getName().endsWith(".java")) {
                files.add(entry);
            }
        }
    }
}
