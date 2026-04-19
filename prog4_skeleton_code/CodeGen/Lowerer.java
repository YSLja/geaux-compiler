package CodeGen;

import Absyn.CompStmt;
import Absyn.DeclList;
import Absyn.Decl;
import Absyn.EmptyExp;
import Absyn.EmptyStmt;
import Absyn.Exp;
import Absyn.ExpList;
import Absyn.ExprStmt;
import Absyn.FunDecl;
import Absyn.FunExp;
import Absyn.ID;
import Absyn.DecLit;
import Absyn.StrLit;
import Absyn.Parameter;
import Absyn.StmtList;
import Absyn.Stmt;
import Absyn.UnaryExp;
import Absyn.VarDecl;
// NOTE: We do NOT import Absyn.BinOp, Absyn.IfStmt, Absyn.ReturnStmt as plain names
// because they would shadow the same-named CodeGen classes (CodeGen.BinOp, etc.).
// Instead, we use them fully-qualified as Absyn.BinOp, Absyn.IfStmt, Absyn.ReturnStmt.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Lowerer — Task 1
 *
 * Walks the AST (Abstract Syntax Tree) produced by the parser and converts
 * it into GOTO IR (Intermediate Representation) defined in ir.java.
 *
 * After lowering, the Emitter converts the IR into valid C code.
 *
 * Pipeline:
 *   .g file → Parser → AST → (Typechecker annotates types) → Lowerer → GOTO IR → Emitter → C code
 *
 * Task 1 handles:
 *   - Variable declarations   → adds Var to Program.globals
 *   - Assignments             → Assign IR node
 *   - Integer/string literals → Literal IR node
 *   - Binary ops (+,-,*,/,<) → BinOp IR node
 *   - Unary ops  (-,!)        → UnaryOp IR node
 *   - Variable references     → Var IR node
 *   - return statements       → ReturnStmt IR node
 *   - if / if-else            → Label + IfStmt + Goto IR nodes
 *   - printf (builtin)        → Printf IR node
 */
public class Lowerer {

    // The GOTO Program we are building as we walk the AST.
    private Program program;

    // The Function we are currently inside.
    // Set each time we enter a FunDecl; instructions are added here.
    private Function currentFunction;

    // Maps original variable name → its Var IR node.
    // Used so that when we see a variable reference (ID), we can find its Var.
    // IMPORTANT: Task 5 will replace this with a rename-based approach for shadowing support.
    private HashMap<String, Var> varMap;

    public Lowerer() {
        this.program         = new Program();
        this.currentFunction = null;
        this.varMap          = new HashMap<>();
    }

    /** Returns the finished Program IR after lowering is done. */
    public Program getProgram() {
        return program;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOP-LEVEL ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Entry point — the root of the AST is always a DeclList.
     * Each item is either a FunDecl (function) or VarDecl (global variable).
     */
    public void visitDeclList(DeclList node) {
        for (Decl decl : node.list) {
            lowerDecl(decl);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DECLARATIONS
    // ─────────────────────────────────────────────────────────────────────────

    /** Routes each declaration to the correct lowering method. */
    private void lowerDecl(Decl decl) {
        if (decl instanceof FunDecl) {
            lowerFunDecl((FunDecl) decl);
        } else if (decl instanceof VarDecl) {
            lowerVarDecl((VarDecl) decl);
        } else if (decl instanceof DeclList) {
            visitDeclList((DeclList) decl);
        }
        // Parameter, StructDecl, etc. are skipped in Task 1.
    }

    /**
     * Lower a function declaration.
     *
     * Example Geaux:
     *   fun int main() { ... }
     *
     * Creates a Function in the IR program and lowers the body statements.
     */
    private void lowerFunDecl(FunDecl node) {
        // Convert the Absyn return type to a C type string ("int", "void", "char*")
        String returnTypeStr = toReturnTypeString(node.type.typeAnnotation);

        // Create and register the Function
        Function func = new Function(node.name, returnTypeStr);
        program.funcs.add(func);

        // Point currentFunction here so statements end up in this function
        currentFunction = func;

        // Lower the function body (usually a CompStmt block { ... })
        lowerStmt(node.body);
    }

    /**
     * Lower a variable declaration.
     *
     * Example Geaux:
     *   var int x = 10;
     *
     * ALL variables in GOTO IR are global, even those declared inside a function.
     * So we always add to program.globals.
     * If there's an initializer (= 10), we also emit an Assign instruction.
     */
    private void lowerVarDecl(VarDecl node) {
        // Get the IR type using the type annotation the typechecker filled in
        Type irType = toIRType(node.type.typeAnnotation);

        // Create the Var IR node and add it to global declarations
        Var varNode = new Var(node.name, irType);
        program.globals.add(varNode);

        // Remember this variable so we can look it up when we see its name later
        varMap.put(node.name, varNode);

        // If there is an initializer AND we are inside a function, emit an assignment.
        // "var int x = 10;" → global declaration of x + "x = 10;" instruction in the function.
        if (!(node.init instanceof EmptyExp) && currentFunction != null) {
            IRExpr initValue = lowerExpr(node.init);
            currentFunction.instr.add(new Assign(varNode, initValue));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATEMENTS
    // ─────────────────────────────────────────────────────────────────────────

    /** Routes each statement to the correct lowering method. */
    private void lowerStmt(Stmt stmt) {
        if (stmt instanceof CompStmt) {
            lowerCompStmt((CompStmt) stmt);

        } else if (stmt instanceof ExprStmt) {
            // An expression used as a statement (e.g. x = 5; or printf(...);)
            lowerExprAsStmt(((ExprStmt) stmt).expression);

        } else if (stmt instanceof Absyn.ReturnStmt) {
            // Must use fully-qualified Absyn.ReturnStmt to avoid clash with CodeGen.ReturnStmt
            lowerReturnStmt((Absyn.ReturnStmt) stmt);

        } else if (stmt instanceof Absyn.IfStmt) {
            // Must use fully-qualified Absyn.IfStmt to avoid clash with CodeGen.IfStmt
            lowerIfStmt((Absyn.IfStmt) stmt);

        } else if (stmt instanceof StmtList) {
            for (Stmt s : ((StmtList) stmt).list) {
                lowerStmt(s);
            }

        } else if (stmt instanceof EmptyStmt) {
            // Nothing to emit for an empty statement
        }
        // WhileStmt → Task 2 (intentionally not handled here)
    }

    /**
     * Lower a compound (block) statement.
     *
     * Example: { var int x = 5; printf("hi\n"); }
     *
     * First lower the local variable declarations (they become globals),
     * then lower each statement inside the block.
     */
    private void lowerCompStmt(CompStmt node) {
        // Lower local variable declarations first
        for (Decl decl : node.decl_list.list) {
            lowerDecl(decl);
        }
        // Then lower each statement in the block
        for (Stmt stmt : node.stmt_list.list) {
            lowerStmt(stmt);
        }
    }

    /**
     * Lower a return statement.
     *
     * Example Geaux:  return x;
     * Emitted IR:     ReturnStmt(Var("x"))
     */
    private void lowerReturnStmt(Absyn.ReturnStmt node) {
        IRExpr value = lowerExpr(node.expression);
        // NOTE: CodeGen.ReturnStmt (not Absyn.ReturnStmt) — they share the name
        currentFunction.instr.add(new ReturnStmt(value));
    }

    /**
     * Lower an if / if-else statement.
     *
     * GOTO IR has no structured if — we build it from labels and jumps.
     *
     * Pattern:
     *   if (cond) goto LABEL1; goto LABEL2;
     *   LABEL1:
     *     [true branch code]
     *     goto LABEL_END;
     *   LABEL2:
     *     [false branch code, if any]
     *   LABEL_END:
     */
    private void lowerIfStmt(Absyn.IfStmt node) {
        // Get unique label names so nothing collides with other if/while blocks
        String trueLabel  = program.getUniqueLabelName(); // entered when condition is true
        String falseLabel = program.getUniqueLabelName(); // entered when condition is false
        String endLabel   = program.getUniqueLabelName(); // all paths merge here

        // Lower the condition expression
        IRExpr cond = lowerExpr(node.expression);

        // Emit: if (cond) goto trueLabel; goto falseLabel;
        // NOTE: CodeGen.IfStmt (not Absyn.IfStmt) — same name, different class
        currentFunction.instr.add(new IfStmt(cond, trueLabel, falseLabel));

        // ── TRUE BRANCH ──────────────────────────────────────────────────────
        currentFunction.instr.add(new Label(trueLabel));
        lowerStmt(node.if_statement);
        currentFunction.instr.add(new Goto(endLabel)); // skip past the false branch

        // ── FALSE BRANCH ─────────────────────────────────────────────────────
        currentFunction.instr.add(new Label(falseLabel));
        if (!(node.else_statement instanceof EmptyStmt)) {
            // There is an else clause — lower it too
            lowerStmt(node.else_statement);
        }

        // ── MERGE POINT ──────────────────────────────────────────────────────
        currentFunction.instr.add(new Label(endLabel));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXPRESSIONS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lower an expression that is used as a statement.
     * Handles special cases: printf builtin, assignments.
     */
    private void lowerExprAsStmt(Exp expr) {
        if (expr instanceof FunExp) {
            FunExp call = (FunExp) expr;
            // Is this a call to the printf builtin?
            if (call.name instanceof ID && ((ID) call.name).value.equals("printf")) {
                lowerPrintf(call);
                return;
            }
            // Other function calls used as statements (result discarded)
            lowerFunCallExpr(call);
            return;
        }

        if (expr instanceof Absyn.AssignExp) {
            // Assignment as a statement — lower it, discard the returned Var
            lowerAssignExpr((Absyn.AssignExp) expr);
            return;
        }

        // Fallback: lower as an expression (result discarded)
        lowerExpr(expr);
    }

    /**
     * Lower an expression that produces a value (an IRExpr).
     * This is the main dispatch method for all expressions.
     */
    private IRExpr lowerExpr(Exp expr) {
        if (expr instanceof DecLit) {
            return lowerDecLit((DecLit) expr);                    // 42

        } else if (expr instanceof StrLit) {
            return lowerStrLit((StrLit) expr);                    // "hello"

        } else if (expr instanceof ID) {
            return lowerID((ID) expr);                            // x

        } else if (expr instanceof Absyn.BinOp) {
            // Fully-qualified to avoid clash with CodeGen.BinOp
            return lowerBinOpExpr((Absyn.BinOp) expr);            // x + y

        } else if (expr instanceof UnaryExp) {
            return lowerUnaryExpr((UnaryExp) expr);               // -x, !flag

        } else if (expr instanceof Absyn.AssignExp) {
            return lowerAssignExpr((Absyn.AssignExp) expr);       // x = 5

        } else if (expr instanceof FunExp) {
            return lowerFunCallExpr((FunExp) expr);               // foo()

        } else {
            throw new RuntimeException(
                "Lowerer: unknown expression type: " + expr.getClass().getName()
            );
        }
    }

    /**
     * Lower an integer literal.
     * Example:  42  →  Literal(42, INT)
     */
    private IRExpr lowerDecLit(DecLit lit) {
        return new Literal(lit.value, Type.INT);
    }

    /**
     * Lower a string literal.
     * Example:  "hello"  →  Literal("hello", STRING)
     */
    private IRExpr lowerStrLit(StrLit lit) {
        return new Literal(lit.value, Type.STRING);
    }

    /**
     * Lower a variable reference.
     * Example:  x  →  Var("x", INT)
     *
     * Looks up the Var in our varMap.
     */
    private IRExpr lowerID(ID id) {
        Var v = varMap.get(id.value);
        if (v == null) {
            throw new RuntimeException(
                "Lowerer: variable used before declaration: '" + id.value + "'"
            );
        }
        return v;
    }

    /**
     * Lower a binary operation.
     * Example:  x + y   →  BinOp("+", Var(x), Var(y), INT)
     * Example:  z < 10  →  BinOp("<", Var(z), Literal(10), INT)
     *
     * NOTE: Absyn.BinOp.oper is the operator string (+, -, *, /, <, etc.)
     *       CodeGen.BinOp.op  is the same thing — just a different field name.
     */
    private IRExpr lowerBinOpExpr(Absyn.BinOp binop) {
        IRExpr left  = lowerExpr(binop.left);
        IRExpr right = lowerExpr(binop.right);
        // All arithmetic and comparison results are INT in Geaux
        // "new BinOp" here refers to CodeGen.BinOp (same package = higher priority)
        return new BinOp(binop.oper, left, right, Type.INT);
    }

    /**
     * Lower a unary operation.
     * Example:  -x    →  UnaryOp("-", Var(x), INT)
     * Example:  !flag →  UnaryOp("!", Var(flag), INT)
     */
    private IRExpr lowerUnaryExpr(UnaryExp unary) {
        IRExpr inner = lowerExpr(unary.exp);
        return new UnaryOp(unary.prefix, inner, Type.INT);
    }

    /**
     * Lower an assignment expression.
     * Example:  x = 5
     *   → Emits:    x = 5;   (an Assign instruction in the current function)
     *   → Returns:  Var(x)   (assignment expression evaluates to the assigned value)
     *
     * For Task 1, the left-hand side must be a plain variable (ID).
     * Array element assignment (arr[i] = x) is handled in Task 3.
     */
    private IRExpr lowerAssignExpr(Absyn.AssignExp assign) {
        if (!(assign.left instanceof ID)) {
            throw new RuntimeException(
                "Lowerer: left side of assignment must be a variable name (Task 3 handles arrays)"
            );
        }

        ID targetId = (ID) assign.left;
        Var target  = varMap.get(targetId.value);
        if (target == null) {
            throw new RuntimeException(
                "Lowerer: undefined variable on left side of assignment: '" + targetId.value + "'"
            );
        }

        IRExpr value = lowerExpr(assign.right);

        // Emit the assignment instruction into the current function
        currentFunction.instr.add(new Assign(target, value));

        return target; // the expression evaluates to the assigned variable
    }

    /**
     * Lower a user-defined function call.
     * Example:  foo()  →  Call("foo", INT)
     *
     * In GOTO IR, all variables are global, so function calls take NO arguments.
     * The return type is determined from the typechecker's annotation.
     */
    private IRExpr lowerFunCallExpr(FunExp call) {
        if (!(call.name instanceof ID)) {
            throw new RuntimeException("Lowerer: function name must be a plain identifier");
        }
        String funcName = ((ID) call.name).value;

        // Use the type annotated by the typechecker for the return type
        Type retType = toIRType(call.typeAnnotation);

        return new Call(funcName, retType);
    }

    /**
     * Lower a printf call.
     *
     * printf is a Geaux builtin — it maps directly to a Printf IR node,
     * which the Emitter then turns into a C printf() call.
     *
     * Geaux:   printf("Sum: %d\n", z)
     * IR:      Printf("Sum: %d\\n", [Var(z)])
     *
     * The first argument must always be a string literal (the format).
     * Remaining arguments are the values to substitute into the format.
     */
    private void lowerPrintf(FunExp call) {
        if (call.params.list.isEmpty()) {
            throw new RuntimeException("printf requires at least one argument (the format string)");
        }

        // First argument = format string (must be a string literal)
        Exp firstArg = call.params.list.get(0);
        if (!(firstArg instanceof StrLit)) {
            throw new RuntimeException("printf's first argument must be a string literal");
        }
        String format = ((StrLit) firstArg).value;

        // Lower the remaining arguments (the values printed by the format)
        List<IRExpr> args = new ArrayList<>();
        for (int i = 1; i < call.params.list.size(); i++) {
            args.add(lowerExpr(call.params.list.get(i)));
        }

        // Emit the Printf builtin instruction
        currentFunction.instr.add(new Printf(format, args));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TYPE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Convert a Typecheck.Types.Type (from the typechecker) to a CodeGen.Type (for the IR).
     *
     * The typechecker annotates every expression with its type.
     * Example: INT() → Type.INT,  STRING() → Type.STRING
     */
    private Type toIRType(Typecheck.Types.Type tcType) {
        if (tcType == null) {
            // Type annotation wasn't set — shouldn't happen after typechecking
            return Type.INT;
        }
        if (tcType instanceof Typecheck.Types.INT) {
            return Type.INT;
        } else if (tcType instanceof Typecheck.Types.STRING) {
            return Type.STRING;
        } else if (tcType instanceof Typecheck.Types.ARRAY) {
            return Type.INTARRAY;  // Geaux only has int[] arrays
        } else {
            // VOID, POINTER, ALIAS, etc. — default to INT
            return Type.INT;
        }
    }

    /**
     * Get the C return type string for a function declaration.
     * Example:  VOID → "void",  INT → "int",  STRING → "char*"
     */
    private String toReturnTypeString(Typecheck.Types.Type tcType) {
        if (tcType instanceof Typecheck.Types.VOID) {
            return "void";
        } else if (tcType instanceof Typecheck.Types.STRING) {
            return "char*";
        } else {
            return "int"; // default (includes INT and anything else)
        }
    }
}
