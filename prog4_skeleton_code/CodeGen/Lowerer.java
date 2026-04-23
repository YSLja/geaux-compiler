package CodeGen;

import Absyn.ArrayExp;
import Absyn.AssignExp;
import Absyn.BreakStmt;
import Absyn.CompStmt;
import Absyn.Decl;
import Absyn.DeclList;
import Absyn.DecLit;
import Absyn.EmptyExp;
import Absyn.EmptyStmt;
import Absyn.Exp;
import Absyn.ExpList;
import Absyn.ExprStmt;
import Absyn.FunDecl;
import Absyn.FunExp;
import Absyn.ID;
import Absyn.Parameter;
import Absyn.Stmt;
import Absyn.StmtList;
import Absyn.StrLit;
import Absyn.UnaryExp;
import Absyn.VarDecl;
import Absyn.WhileStmt;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lowerer {

    private static final class VarBinding {
        final Var valueVar;
        final Var sizeVar;

        VarBinding(Var valueVar, Var sizeVar) {
            this.valueVar = valueVar;
            this.sizeVar = sizeVar;
        }

        boolean isArray() {
            return this.sizeVar != null;
        }
    }

    private static final class FunBinding {
        final String emittedName;
        final String returnType;
        final Typecheck.Types.Type sourceReturnType;
        final ArrayList<VarBinding> params = new ArrayList<>();

        FunBinding(String emittedName, String returnType, Typecheck.Types.Type sourceReturnType) {
            this.emittedName = emittedName;
            this.returnType = returnType;
            this.sourceReturnType = sourceReturnType;
        }
    }

    private final Program program;
    private final Deque<Map<String, VarBinding>> varScopes;
    private final Deque<Map<String, FunBinding>> funScopes;
    private final Deque<String> loopEndLabels;

    private Function currentFunction;
    private Function mainFunction;
    private Function globalInitFunction;

    public Lowerer() {
        this.program = new Program();
        this.varScopes = new ArrayDeque<>();
        this.funScopes = new ArrayDeque<>();
        this.loopEndLabels = new ArrayDeque<>();
        pushScope();
    }

    public Program getProgram() {
        return program;
    }

    public void visitDeclList(DeclList node) {
        for (Decl decl : node.list) {
            lowerDecl(decl);
        }
        injectGlobalInitCall();
    }

    private void lowerDecl(Decl decl) {
        if (decl instanceof FunDecl) {
            lowerFunDecl((FunDecl) decl);
        } else if (decl instanceof VarDecl) {
            lowerVarDecl((VarDecl) decl);
        } else if (decl instanceof DeclList) {
            for (Decl nested : ((DeclList) decl).list) {
                lowerDecl(nested);
            }
        }
    }

    private void lowerFunDecl(FunDecl node) {
        boolean preserveEntryMain = isTopLevelFunction() && "main".equals(node.name);
        String emittedName = preserveEntryMain ? "main" : node.name + program.getUniqueFunctionName();
        FunBinding binding = new FunBinding(emittedName, toReturnTypeString(node.type.typeAnnotation), node.type.typeAnnotation);
        currentFunScope().put(node.name, binding);

        Function previousFunction = currentFunction;
        Function function = new Function(binding.emittedName, binding.returnType);
        program.funcs.add(function);
        currentFunction = function;
        if (preserveEntryMain) {
            mainFunction = function;
        }

        pushScope();
        for (Decl paramDecl : node.params.list) {
            Parameter param = (Parameter) paramDecl;
            VarBinding paramBinding = declareVariable(param.name, param.type);
            binding.params.add(paramBinding);
        }
        lowerStmt(node.body);
        popScope();

        currentFunction = previousFunction;
    }

    private void lowerVarDecl(VarDecl node) {
        VarBinding binding = declareVariable(node.name, node.type);
        if (hasNoInitializer(node.init)) {
            return;
        }

        Function savedFunction = currentFunction;
        if (currentFunction == null) {
            currentFunction = ensureGlobalInitFunction();
        }

        if (binding.isArray()) {
            if (!(node.init instanceof ExpList)) {
                throw new RuntimeException("Array initializer must be an expression list");
            }
            lowerArrayInitializer(binding, (ExpList) node.init);
        } else {
            currentFunction.instr.add(new Assign(binding.valueVar, lowerExpr(node.init)));
        }

        currentFunction = savedFunction;
    }

    private void lowerArrayInitializer(VarBinding binding, ExpList initList) {
        for (int i = 0; i < initList.list.size(); i++) {
            IRExpr index = new Literal(i, Type.INT);
            IRExpr value = lowerExpr(initList.list.get(i));
            emitArrayStore(binding, index, value);
        }
    }

    private void lowerStmt(Stmt stmt) {
        if (stmt instanceof CompStmt) {
            lowerCompStmt((CompStmt) stmt);
        } else if (stmt instanceof ExprStmt) {
            lowerExprAsStmt(((ExprStmt) stmt).expression);
        } else if (stmt instanceof Absyn.ReturnStmt) {
            lowerReturnStmt((Absyn.ReturnStmt) stmt);
        } else if (stmt instanceof Absyn.IfStmt) {
            lowerIfStmt((Absyn.IfStmt) stmt);
        } else if (stmt instanceof WhileStmt) {
            lowerWhileStmt((WhileStmt) stmt);
        } else if (stmt instanceof BreakStmt) {
            lowerBreakStmt();
        } else if (stmt instanceof StmtList) {
            for (Stmt nested : ((StmtList) stmt).list) {
                lowerStmt(nested);
            }
        } else if (!(stmt instanceof EmptyStmt)) {
            throw new RuntimeException("Unsupported statement: " + stmt.getClass().getName());
        }
    }

    private void lowerCompStmt(CompStmt node) {
        for (Decl decl : node.decl_list.list) {
            lowerDecl(decl);
        }
        for (Stmt stmt : node.stmt_list.list) {
            lowerStmt(stmt);
        }
    }

    private void lowerReturnStmt(Absyn.ReturnStmt node) {
        currentFunction.instr.add(new ReturnStmt(lowerExpr(node.expression)));
    }

    private void lowerIfStmt(Absyn.IfStmt node) {
        pushScope();

        String trueLabel = program.getUniqueLabelName();
        String falseLabel = program.getUniqueLabelName();
        String endLabel = program.getUniqueLabelName();

        IRExpr cond = materializeIfSequencingSensitive(node.expression, lowerExpr(node.expression));
        currentFunction.instr.add(new IfStmt(cond, trueLabel, falseLabel));

        currentFunction.instr.add(new Label(trueLabel));
        lowerStmt(node.if_statement);
        currentFunction.instr.add(new Goto(endLabel));

        currentFunction.instr.add(new Label(falseLabel));
        if (!(node.else_statement instanceof EmptyStmt)) {
            lowerStmt(node.else_statement);
        }

        currentFunction.instr.add(new Label(endLabel));
        popScope();
    }

    private void lowerWhileStmt(WhileStmt node) {
        pushScope();

        String startLabel = program.getUniqueLabelName();
        String bodyLabel = program.getUniqueLabelName();
        String endLabel = program.getUniqueLabelName();

        currentFunction.instr.add(new Label(startLabel));
        IRExpr cond = materializeIfSequencingSensitive(node.expression, lowerExpr(node.expression));
        currentFunction.instr.add(new IfStmt(cond, bodyLabel, endLabel));

        currentFunction.instr.add(new Label(bodyLabel));
        loopEndLabels.push(endLabel);
        lowerStmt(node.statement);
        loopEndLabels.pop();
        currentFunction.instr.add(new Goto(startLabel));
        currentFunction.instr.add(new Label(endLabel));

        popScope();
    }

    private void lowerBreakStmt() {
        if (loopEndLabels.isEmpty()) {
            throw new RuntimeException("break used outside of a loop");
        }
        currentFunction.instr.add(new Goto(loopEndLabels.peek()));
    }

    private void lowerExprAsStmt(Exp expr) {
        if (expr instanceof AssignExp) {
            AssignExp assign = (AssignExp) expr;
            lowerAssignment(assign.left, lowerExpr(assign.right));
            return;
        }

        if (expr instanceof FunExp) {
            FunExp call = (FunExp) expr;
            String funcName = getCallName(call);

            if ("printf".equals(funcName)) {
                lowerPrintf(call);
                return;
            }
            if ("writeToFile".equals(funcName)) {
                lowerWriteToFile(call);
                return;
            }
            if ("input".equals(funcName)) {
                lowerInputStatement(call);
                return;
            }
            if ("readFromFile".equals(funcName)) {
                throw new RuntimeException("readFromFile must be used as an expression");
            }

            currentFunction.instr.add(new Eval(lowerUserFunCall(call)));
            return;
        }

        currentFunction.instr.add(new Eval(lowerExpr(expr)));
    }

    private void lowerPrintf(FunExp call) {
        if (call.params.list.isEmpty()) {
            throw new RuntimeException("printf requires a format string");
        }
        Exp formatArg = call.params.list.get(0);
        if (!(formatArg instanceof StrLit)) {
            throw new RuntimeException("printf's first argument must be a string literal");
        }

        List<IRExpr> args = new ArrayList<>();
        for (int i = 1; i < call.params.list.size(); i++) {
            args.add(lowerExpr(call.params.list.get(i)));
        }
        currentFunction.instr.add(new Printf(((StrLit) formatArg).value, args));
    }

    private void lowerWriteToFile(FunExp call) {
        if (call.params.list.size() != 2) {
            throw new RuntimeException("writeToFile expects exactly 2 arguments");
        }
        currentFunction.instr.add(new WriteToFile(
                lowerExpr(call.params.list.get(0)),
                lowerExpr(call.params.list.get(1))));
    }

    private void lowerInputStatement(FunExp call) {
        if (call.params.list.isEmpty()) {
            currentFunction.instr.add(new Eval(new Input()));
            return;
        }
        if (call.params.list.size() != 1) {
            throw new RuntimeException("input expects 0 or 1 arguments");
        }
        lowerAssignment(call.params.list.get(0), new Input());
    }

    private IRExpr lowerExpr(Exp expr) {
        if (expr instanceof DecLit) {
            return new Literal(((DecLit) expr).value, Type.INT);
        }
        if (expr instanceof StrLit) {
            return new Literal(((StrLit) expr).value, Type.STRING);
        }
        if (expr instanceof ID) {
            return lowerID((ID) expr);
        }
        if (expr instanceof ArrayExp) {
            return lowerArrayExpr((ArrayExp) expr);
        }
        if (expr instanceof Absyn.BinOp) {
            return lowerBinOpExpr((Absyn.BinOp) expr);
        }
        if (expr instanceof UnaryExp) {
            return lowerUnaryExpr((UnaryExp) expr);
        }
        if (expr instanceof AssignExp) {
            AssignExp assign = (AssignExp) expr;
            return lowerAssignment(assign.left, lowerExpr(assign.right));
        }
        if (expr instanceof FunExp) {
            return lowerCallExpr((FunExp) expr);
        }
        if (expr instanceof EmptyExp) {
            throw new RuntimeException("Unexpected empty expression during lowering");
        }
        if (expr instanceof ExpList) {
            throw new RuntimeException("Expression lists can only appear as initializers");
        }
        throw new RuntimeException("Unsupported expression: " + expr.getClass().getName());
    }

    private IRExpr lowerID(ID id) {
        VarBinding binding = resolveVariable(id.value);
        if (binding == null) {
            throw new RuntimeException("Unknown variable: " + id.value);
        }
        return binding.valueVar;
    }

    private IRExpr lowerArrayExpr(ArrayExp expr) {
        if (!(expr.name instanceof ID)) {
            throw new RuntimeException("Only plain array variables are supported");
        }
        if (expr.index_list.list.size() != 1) {
            throw new RuntimeException("Only one-dimensional arrays are supported");
        }

        VarBinding binding = resolveVariable(((ID) expr.name).value);
        if (binding == null || !binding.isArray()) {
            throw new RuntimeException("Array access requires an array variable");
        }

        IRExpr index = materializeIfSequencingSensitive(expr.index_list.list.get(0), lowerExpr(expr.index_list.list.get(0)));
        return new ArrayLoad(binding.valueVar, binding.sizeVar, index, Type.INT);
    }

    private IRExpr lowerBinOpExpr(Absyn.BinOp expr) {
        IRExpr left = materializeIfSequencingSensitive(expr.left, lowerExpr(expr.left));
        IRExpr right = materializeIfSequencingSensitive(expr.right, lowerExpr(expr.right));
        return new BinOp(expr.oper, left, right, Type.INT);
    }

    private IRExpr lowerUnaryExpr(UnaryExp expr) {
        IRExpr inner = materializeIfSequencingSensitive(expr.exp, lowerExpr(expr.exp));
        return new UnaryOp(expr.prefix, inner, Type.INT);
    }

    private IRExpr lowerCallExpr(FunExp call) {
        String funcName = getCallName(call);
        return switch (funcName) {
            case "printf" -> throw new RuntimeException("printf can only be used as a statement");
            case "writeToFile" -> throw new RuntimeException("writeToFile can only be used as a statement");
            case "readFromFile" -> lowerReadFromFile(call);
            case "input" -> lowerInputExpr(call);
            default -> lowerUserFunCall(call);
        };
    }

    private IRExpr lowerReadFromFile(FunExp call) {
        if (call.params.list.size() != 1) {
            throw new RuntimeException("readFromFile expects exactly 1 argument");
        }
        return new ReadFromFile(lowerExpr(call.params.list.get(0)));
    }

    private IRExpr lowerInputExpr(FunExp call) {
        if (!call.params.list.isEmpty()) {
            throw new RuntimeException("input(target) is only allowed in statement position");
        }
        return new Input();
    }

    private IRExpr lowerUserFunCall(FunExp call) {
        FunBinding callee = resolveFunction(getCallName(call));
        if (callee == null) {
            throw new RuntimeException("Unknown function: " + getCallName(call));
        }
        if (callee.params.size() != call.params.list.size()) {
            throw new RuntimeException("Mismatched argument count when lowering call to " + getCallName(call));
        }

        for (int i = 0; i < call.params.list.size(); i++) {
            Exp argExpr = call.params.list.get(i);
            VarBinding param = callee.params.get(i);

            if (param.isArray()) {
                if (!(argExpr instanceof ID)) {
                    throw new RuntimeException("Array arguments must be array variables");
                }
                VarBinding argBinding = resolveVariable(((ID) argExpr).value);
                if (argBinding == null || !argBinding.isArray()) {
                    throw new RuntimeException("Array argument must resolve to an array variable");
                }
                currentFunction.instr.add(new Assign(param.valueVar, argBinding.valueVar));
                currentFunction.instr.add(new Assign(param.sizeVar, argBinding.sizeVar));
                continue;
            }

            currentFunction.instr.add(new Assign(param.valueVar, lowerExpr(argExpr)));
        }

        return new Call(callee.emittedName, toIRType(callee.sourceReturnType));
    }

    private IRExpr lowerAssignment(Exp targetExpr, IRExpr valueExpr) {
        if (targetExpr instanceof ID) {
            VarBinding target = resolveVariable(((ID) targetExpr).value);
            if (target == null) {
                throw new RuntimeException("Unknown assignment target: " + ((ID) targetExpr).value);
            }
            currentFunction.instr.add(new Assign(target.valueVar, valueExpr));
            return target.valueVar;
        }

        if (targetExpr instanceof ArrayExp) {
            ArrayExp arrayTarget = (ArrayExp) targetExpr;
            if (!(arrayTarget.name instanceof ID)) {
                throw new RuntimeException("Only plain array variables are supported as assignment targets");
            }
            if (arrayTarget.index_list.list.size() != 1) {
                throw new RuntimeException("Only one-dimensional arrays are supported");
            }

            VarBinding target = resolveVariable(((ID) arrayTarget.name).value);
            if (target == null || !target.isArray()) {
                throw new RuntimeException("Array assignment target must be an array variable");
            }

            IRExpr stableIndex = materialize(lowerExpr(arrayTarget.index_list.list.get(0)));
            IRExpr stableValue = materialize(valueExpr);
            emitArrayStore(target, stableIndex, stableValue);
            return stableValue;
        }

        throw new RuntimeException("Unsupported assignment target: " + targetExpr.getClass().getName());
    }

    private void emitArrayStore(VarBinding binding, IRExpr indexExpr, IRExpr valueExpr) {
        IRExpr requestedSize = new BinOp("+", indexExpr, new Literal(1, Type.INT), Type.INT);
        currentFunction.instr.add(new ArrayAlloc(binding.valueVar, binding.sizeVar, requestedSize));
        currentFunction.instr.add(new ArrayStore(binding.valueVar, binding.sizeVar, indexExpr, valueExpr));
    }

    private IRExpr materialize(IRExpr expr) {
        if (expr instanceof Var || expr instanceof Literal) {
            return expr;
        }
        Var temp = createGlobalVar(expr.type);
        currentFunction.instr.add(new Assign(temp, expr));
        return temp;
    }

    private IRExpr materializeIfSequencingSensitive(Exp sourceExpr, IRExpr loweredExpr) {
        if (sourceExpr instanceof FunExp || sourceExpr instanceof ArrayExp) {
            return materialize(loweredExpr);
        }
        return loweredExpr;
    }

    private VarBinding declareVariable(String sourceName, Absyn.Type sourceType) {
        boolean isArray = !sourceType.brackets.list.isEmpty();
        Var valueVar = createGlobalVar(isArray ? Type.INTARRAY : toIRType(sourceType.typeAnnotation));
        Var sizeVar = isArray ? createGlobalVar(Type.INT) : null;
        VarBinding binding = new VarBinding(valueVar, sizeVar);
        currentVarScope().put(sourceName, binding);
        return binding;
    }

    private Var createGlobalVar(Type type) {
        Var var = new Var(program.getUniqueVarName(), type);
        program.globals.add(var);
        return var;
    }

    private Function ensureGlobalInitFunction() {
        if (globalInitFunction == null) {
            globalInitFunction = new Function("geaux_global_init" + program.getUniqueFunctionName(), "void");
            program.funcs.add(globalInitFunction);
        }
        return globalInitFunction;
    }

    private void injectGlobalInitCall() {
        if (globalInitFunction == null || globalInitFunction.instr.isEmpty()) {
            return;
        }
        if (mainFunction == null) {
            throw new RuntimeException("Global initializers require a main function");
        }
        mainFunction.instr.add(0, new Eval(new Call(globalInitFunction.name, Type.INT)));
    }

    private boolean hasNoInitializer(Exp init) {
        return init instanceof EmptyExp || (init instanceof ExpList && ((ExpList) init).list.isEmpty());
    }

    private String getCallName(FunExp call) {
        if (!(call.name instanceof ID)) {
            throw new RuntimeException("Function name must be a plain identifier");
        }
        return ((ID) call.name).value;
    }

    private boolean isTopLevelFunction() {
        return currentFunction == null && funScopes.size() == 1;
    }

    private VarBinding resolveVariable(String name) {
        for (Map<String, VarBinding> scope : varScopes) {
            VarBinding binding = scope.get(name);
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private FunBinding resolveFunction(String name) {
        for (Map<String, FunBinding> scope : funScopes) {
            FunBinding binding = scope.get(name);
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private Map<String, VarBinding> currentVarScope() {
        return varScopes.peek();
    }

    private Map<String, FunBinding> currentFunScope() {
        return funScopes.peek();
    }

    private void pushScope() {
        varScopes.push(new HashMap<>());
        funScopes.push(new HashMap<>());
    }

    private void popScope() {
        varScopes.pop();
        funScopes.pop();
    }

    private Type toIRType(Typecheck.Types.Type tcType) {
        if (tcType instanceof Typecheck.Types.STRING) {
            return Type.STRING;
        }
        if (tcType instanceof Typecheck.Types.ARRAY) {
            return Type.INTARRAY;
        }
        return Type.INT;
    }

    private String toReturnTypeString(Typecheck.Types.Type tcType) {
        if (tcType instanceof Typecheck.Types.VOID) {
            return "void";
        }
        if (tcType instanceof Typecheck.Types.STRING) {
            return "char*";
        }
        if (tcType instanceof Typecheck.Types.ARRAY) {
            return "int*";
        }
        return "int";
    }
}
