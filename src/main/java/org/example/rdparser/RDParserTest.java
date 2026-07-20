package org.example.rdparser;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;

public class RDParserTest {
    
    private static int total = 0;
    private static int success = 0;
    private static int failed = 0;
    private static List<String> failedFiles = new ArrayList<>();
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: RDParserTest <java-file-or-directory>");
            return;
        }
        
        // Initialize ASTHelper first
        try {
            Class.forName("org.example.rdparser.ASTHelper");
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize ASTHelper: " + e.getMessage());
        }
        
        File fileOrDir = new File(args[0]);
        if (fileOrDir.isDirectory()) {
            testDirectory(fileOrDir);
        } else {
            testFile(fileOrDir);
        }
    }
    
    private static void testDirectory(File dir) {
        System.out.println("Scanning directory: " + dir.getAbsolutePath());
        List<File> javaFiles = new ArrayList<>();
        collectJavaFiles(dir, javaFiles);
        
        if (javaFiles.isEmpty()) {
            System.out.println("No Java files found");
            return;
        }
        System.out.println("Found " + javaFiles.size() + " Java files\n");
        
        for (File file : javaFiles) {
            testSingleFile(file);
        }
        
        System.out.println("\n=== Summary ===");
        System.out.println("Total: " + total);
        System.out.println("Success: " + success);
        System.out.println("Failed: " + failed);
        
        if (!failedFiles.isEmpty()) {
            System.out.println("\n=== Failed files ===");
            for (String f : failedFiles) {
                System.out.println("  " + f);
            }
        }
    }
    
    private static void testSingleFile(File file) {
        total++;
        String relativePath = getRelativePath(file);
        System.out.print("[" + total + "] " + relativePath + "... ");
        try {
            TestResult result = testFile(file);
            if (result.success) {
                success++;
                System.out.println("OK");
            } else {
                failed++;
                failedFiles.add(relativePath);
                System.out.println("FAILED");
                if (result.error != null && !result.error.isEmpty()) {
                    System.out.println("    Error: " + result.error);
                }
            }
        } catch (Exception e) {
            failed++;
            failedFiles.add(relativePath);
            System.out.println("ERROR: " + e.getMessage());
        }
    }
    
    private static String getRelativePath(File file) {
        String path = file.getAbsolutePath();
        // Try to get a shorter relative path
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
    
    private static IErrorHandlingPolicy getPolicy() {
        try {
            for (Field f : DefaultErrorHandlingPolicies.class.getFields()) {
                if (f.getName().contains("Problems")) {
                    Object val = f.get(null);
                    if (val instanceof IErrorHandlingPolicy) {
                        return (IErrorHandlingPolicy) val;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // Create a simple policy using anonymous class
        return new IErrorHandlingPolicy() {
            @Override
            public boolean stopOnFirstError() { return false; }
            @Override
            public boolean proceedOnErrors() { return true; }
            public boolean ignoreDuplicateProblems() { return false; }
            public boolean ignoreAllProblems() { return false; }
            @Override
            public boolean ignoreAllErrors() { return false; }
        };
    }
    
    private static TestResult testFile(File file) {
        TestResult result = new TestResult();
        try {
            char[] source = new String(Files.readAllBytes(file.toPath())).toCharArray();
            
            CompilerOptions options = new CompilerOptions();
            options.sourceLevel = ClassFileConstants.JDK11;
            options.complianceLevel = ClassFileConstants.JDK11;
            
            IErrorHandlingPolicy policy = getPolicy();
            ProblemReporter problemReporter = new ProblemReporter(
                policy, options, new DefaultProblemFactory());
            
            RDParser parser = new RDParser(problemReporter, false);
            
            ICompilationUnit unit = new SimpleCompilationUnit(source, file.getName());
            
            Constructor<CompilationResult> crConstructor = CompilationResult.class.getDeclaredConstructor(
                ICompilationUnit.class, int.class, int.class, int.class);
            crConstructor.setAccessible(true);
            CompilationResult compilationResult = crConstructor.newInstance(unit, 1, 1, 0);
            
            try {
                parser.parse(unit, compilationResult);
            } catch (AbortCompilation e) {
                // AbortCompilation means there was a syntax error
                result.success = false;
                if (e.problem != null) {
                    result.error = e.problem.getMessage();
                } else {
                    result.error = "Syntax error";
                }
                return result;
            }
            
            IProblem[] problems = compilationResult.getProblems();
            if (problems != null && problems.length > 0) {
                StringBuilder errorMessages = new StringBuilder();
                for (IProblem problem : problems) {
                    if (problem.isError()) {
                        if (errorMessages.length() > 0) errorMessages.append("; ");
                        errorMessages.append(problem.getMessage());
                    }
                }
                if (errorMessages.length() > 0) {
                    result.success = false;
                    result.error = errorMessages.toString();
                    return result;
                }
            }
            
            result.success = true;
        } catch (Exception e) {
            result.success = false;
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            result.error = e.getMessage();
        }
        return result;
    }
    
    static class TestResult {
        boolean success = false;
        String error = null;
    }
    
    static class SimpleCompilationUnit implements ICompilationUnit {
        private final char[] source;
        private final String fileName;
        
        SimpleCompilationUnit(char[] source, String fileName) {
            this.source = source;
            this.fileName = fileName;
        }
        
        @Override
        public char[] getContents() {
            return source;
        }
        
        @Override
        public char[] getFileName() {
            return fileName.toCharArray();
        }
        
        @Override
        public char[] getMainTypeName() {
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                return fileName.substring(0, dot).toCharArray();
            }
            return fileName.toCharArray();
        }
        
        @Override
        public char[][] getPackageName() {
            return new char[0][];
        }
        
        @Override
        public boolean ignoreOptionalProblems() {
            return false;
        }
    }
}
