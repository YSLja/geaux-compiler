package CodeGen;

// The CodeGen package contains IR classes (Program, Function, Var, etc.)
// that are package-private — so Main must live here to access them.

import Parse.ASTBuilder;
import Parse.antlr_build.Parse.gLexer;
import Parse.antlr_build.Parse.gParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import Typecheck.TypeCheckException;
import Typecheck.Pass.TypeAnnotationPass;
import Typecheck.Pass.CreateScopePass;
import Typecheck.Pass.TypeScopePass;
import Typecheck.Pass.FunAndVarScopePass;
import Typecheck.Pass.JudgementsPass;
import Typecheck.SymbolTable.Scope;
import Typecheck.SymbolTable.FunSymbol;
import Typecheck.Types.LIST;
import Typecheck.Types.VOID;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Main — Geaux Compiler Driver
 *
 * Ties all four stages together:
 *   1. Parse      — lex and parse the .g file into an AST
 *   2. Typecheck  — annotate the AST with types and check for errors
 *   3. Lower      — convert the typed AST into GOTO IR  (Lowerer.java)
 *   4. Emit       — convert GOTO IR into C code          (Emitter.java)
 * Then invokes gcc to compile the C file, and runs the binary.
 *
 * Usage (after running compile.sh):
 *   bash run.sh Examples/helloworld.g
 *   bash run.sh Examples/basics.g
 */
public class Main {

    public static void main(String[] args) throws Exception {

        // ── Argument check ────────────────────────────────────────────────────
        if (args.length < 1) {
            System.err.println("Usage: java CodeGen.Main <file.g>");
            System.err.println("Example: java CodeGen.Main Examples/helloworld.g");
            System.exit(1);
        }

        String inputFile = args[0];

        // ── STAGE 1: Lex + Parse ───────────────────────────────────────────────
        // Read the .g source file into an ANTLR character stream
        CharStream input = CharStreams.fromFileName(inputFile);

        // Break the file into tokens using the Geaux lexer
        gLexer lexer = new gLexer(input);

        // Wrap tokens in a stream the parser can read from
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // Parse using the Geaux grammar, starting at the top-level "program" rule
        gParser parser = new gParser(tokens);
        ParseTree parseTree = parser.program();

        // Convert the parse tree into an AST (Abstract Syntax Tree)
        // The result is an Absyn.DeclList — the list of top-level declarations
        ASTBuilder astBuilder = new ASTBuilder();
        Absyn.DeclList ast = (Absyn.DeclList) astBuilder.visit(parseTree);

        // ── STAGE 2: Typecheck ─────────────────────────────────────────────────
        // These passes annotate every AST node with its type and scope.
        // They MUST run in exactly this order.
        try {
            // Pass 1: Fill in typeAnnotation on every Absyn.Type node
            TypeAnnotationPass tap = new TypeAnnotationPass();
            ast.accept(tap);

            // Pass 2: Create scope objects for each block boundary (functions, blocks)
            CreateScopePass csp = new CreateScopePass();
            ast.accept(csp);

            // ── Register built-in functions into the global scope ────────────────
            // The typechecker doesn't know about Geaux builtins (printf, input, etc.)
            // so we add them manually before the judgements pass runs.
            // printf: takes a string format + any args, returns void (we use INT as a stand-in)
            registerBuiltins(csp.globalscope);

            // Pass 3: Register type aliases (typedef) into the scope
            TypeScopePass tsp = new TypeScopePass(csp.globalscope);
            ast.accept(tsp);

            // Pass 4: Register all function and variable names into their scopes
            FunAndVarScopePass fvcp = new FunAndVarScopePass(csp.globalscope);
            ast.accept(fvcp);

            // Pass 5: Check all type rules and annotate every expression with its type
            JudgementsPass jp = new JudgementsPass(csp.globalscope);
            ast.accept(jp);

            System.out.println("Type check passed!");

        } catch (TypeCheckException e) {
            System.err.println("Type error: " + e.getMessage());
            System.exit(1);
        }

        // ── STAGE 3: Lower AST → GOTO IR ──────────────────────────────────────
        Lowerer lowerer = new Lowerer();
        lowerer.visitDeclList(ast);
        Program program = lowerer.getProgram(); // accessible because we're in package CodeGen

        // ── STAGE 4: Emit GOTO IR → C code ────────────────────────────────────
        Emitter.ProgramEmitter emitter = new Emitter.ProgramEmitter(
            program.globals,
            program.funcs
        );
        String cCode = emitter.emitProgram();

        // Write the C code to a .c file next to the .g file
        // Example: Examples/helloworld.g → Examples/helloworld.c
        String basePath = inputFile.replaceAll("\\.g$", "");
        String cFile    = basePath + ".c";

        try (PrintWriter writer = new PrintWriter(cFile)) {
            writer.print(cCode);
        }
        System.out.println("Generated C: " + cFile);

        // ── STAGE 5: Compile with gcc ──────────────────────────────────────────
        // Output binary: same name as the .g file but without the extension
        String binaryPath = isWindows() ? basePath + ".exe" : basePath;

        ProcessBuilder gccBuilder = new ProcessBuilder("gcc", "-o", binaryPath, cFile);
        gccBuilder.inheritIO(); // show gcc output/errors in the terminal
        int gccExitCode = gccBuilder.start().waitFor();

        if (gccExitCode != 0) {
            System.err.println("gcc failed! See the generated C file: " + cFile);
            System.exit(1);
        }
        System.out.println("Compiled binary: " + binaryPath);

        // ── STAGE 6: Run the compiled binary ───────────────────────────────────
        // On Mac/Linux the binary needs "./" prefix to run from current directory
        String runCmd = isWindows() ? binaryPath : "./" + binaryPath;
        ProcessBuilder runBuilder = new ProcessBuilder(runCmd);
        runBuilder.inheritIO(); // show the program's output in the terminal
        runBuilder.start().waitFor();
    }

    /**
     * Register Geaux builtin functions into the global scope.
     *
     * The typechecker has no built-in knowledge of printf, input, etc.
     * Adding them here prevents "Undefined variable: printf" errors.
     *
     * For now we register printf as returning VOID with no params.
     * The Lowerer handles printf specially anyway (using the Printf IR node).
     */
    private static void registerBuiltins(Scope globalScope) {
        // An empty parameter list — printf is variadic, but the typechecker
        // only needs to know it exists, not the exact types of its arguments.
        LIST emptyParams = new LIST(new ArrayList<>());

        // Register printf: printf("format", ...) → void
        globalScope.addFun("printf",      new FunSymbol("printf",      emptyParams, new VOID()));

        // Register other builtins (Task 4 will fully implement these)
        globalScope.addFun("input",       new FunSymbol("input",       emptyParams, new VOID()));
        globalScope.addFun("readFromFile",  new FunSymbol("readFromFile",  emptyParams, new VOID()));
        globalScope.addFun("writeToFile", new FunSymbol("writeToFile", emptyParams, new VOID()));
    }

    /** Returns true if we are running on Windows (to handle .exe extension). */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
