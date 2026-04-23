package Typecheck.Pass;
import Typecheck.Types.*;
import Typecheck.SymbolTable.*;
import Typecheck.TypeCheckException;
import java.util.ArrayList;

// This pass implements the type rules.
// Some of the logic has been implemented for you in the Types.
// Check out the "canAccept" functions.
public class JudgementsPass extends ScopePass<Void> {

   // Tracks the declared return type of the function we are currently inside.
   // Saved and restored as we enter/exit nested function declarations.
   private Type currentReturnType = null;

   public JudgementsPass(Scope s) {
      super(s);
   }


   //FOUNDATIONAL VISITS

   @Override
   public Void visitDecLit(Absyn.DecLit node) {
	   node.typeAnnotation = new INT();
	   return null;
   }

   @Override
   public Void visitStrLit(Absyn.StrLit node) {
	   node.typeAnnotation = new STRING();
	   return null;
   }

   @Override
   public Void visitID(Absyn.ID node) {
	   if (currentscope.hasVar(node.value)) {
		   node.typeAnnotation = currentscope.getVar(node.value).type;
	   } else if (currentscope.hasFun(node.value)) {
		   node.typeAnnotation = currentscope.getFun(node.value).returnType;
	   } else {
	   	throw new TypeCheckException("Undefined variable: " + node.value);
	   }

	   return null;
   }


   //RULE 13

   @Override
   public Void visitIfStmt(Absyn.IfStmt node) {	   
	   super.visitIfStmt(node);

	   Type expressionType = node.expression.typeAnnotation;
	   if (!(expressionType instanceof INT)) {
		   throw new TypeCheckException("If statement condition must be a number!");
	   }

	   return null;
   }

   @Override
   public Void visitWhileStmt(Absyn.WhileStmt node) {
	   super.visitWhileStmt(node);

	   Type expressionType = node.expression.typeAnnotation;
	   if (!(expressionType instanceof INT)) {
	   	throw new TypeCheckException("While statement condition must be a number!");
	   }

	   return null;
   }


   //RULE 8
   
   @Override
   public Void visitBinOp(Absyn.BinOp node) {
	   super.visitBinOp(node);

	   Type leftType = node.left.typeAnnotation;
	   Type rightType = node.right.typeAnnotation;
	   if (!(leftType instanceof INT)) {
		   throw new TypeCheckException("Left side of the binary operator must be a number!");
	   }
	   if (!(rightType instanceof INT)) {
		   throw new TypeCheckException("Right side of the binary operator must be a number!");
	   }

	   node.typeAnnotation = new INT();
	   return null;
   }


   //RULE 1
   
   @Override
   public Void visitVarDecl(Absyn.VarDecl node) {
	   super.visitVarDecl(node);

	   // The parser uses either an EmptyExp or an empty ExpList to signal
	   // "no initializer" (var int z;). Skip the type check in both cases.
	   boolean hasNoInit = (node.init instanceof Absyn.EmptyExp) ||
	                       (node.init instanceof Absyn.ExpList &&
	                        ((Absyn.ExpList) node.init).list.isEmpty());

	   if (!hasNoInit) {
		Type declaredType = currentscope.getVar(node.name).type;	
	   	Type initializedType = node.init.typeAnnotation;
	   	if (!declaredType.canAccept(initializedType)) {
		   	throw new TypeCheckException("Initialized variable must match the declared variable type!");
	   	}
	   }
	   return null;

   }


   //RULE 2

   @Override
   public Void visitAssignExp(Absyn.AssignExp node) {
   	super.visitAssignExp(node);

	Type left = node.left.typeAnnotation;
	Type right = node.right.typeAnnotation;
	if (!(left.canAccept(right))) {
		throw new TypeCheckException("Assigned value must match the declared variable type!");
	}

	node.typeAnnotation = left;
	return null;
   }


   //RULE 14

   @Override
   public Void visitUnaryExp(Absyn.UnaryExp node) {
   	super.visitUnaryExp(node);

	Type expType = node.exp.typeAnnotation;

	if (node.prefix.equals("*")) {
		if (!(expType instanceof POINTER)) {
			throw new TypeCheckException("The * operator requires a pointer!");
		}
		node.typeAnnotation = ((POINTER) expType).type;
	} else if (node.prefix.equals("&")) {
		node.typeAnnotation = new POINTER(expType);
	} else {
		if (!(expType instanceof INT)) {
			throw new TypeCheckException("Unary operators require a number!");
		}
		node.typeAnnotation = new INT();
	}

	return null;
   }


   // -----------------------------------------------------------------------
   // RULE 4 (helper) — track the current function's return type
   //
   // We override visitFunDecl so that when we descend into a function body
   // we know what return type to expect in visitReturnStmt.
   // ScopePass.visitFunDecl handles switching the scope for us; we just
   // wrap it to save/restore the return type field.
   // -----------------------------------------------------------------------
   @Override
   public Void visitFunDecl(Absyn.FunDecl node) {
      // Save whatever return type was active before (handles nested functions)
      Type previousReturnType = currentReturnType;

      // The declared return type is stored on the function's Type sub-node
      // after TypeAnnotationPass has run.
      currentReturnType = node.type.typeAnnotation;

      // Let ScopePass switch into the function's scope, then visit children.
      super.visitFunDecl(node);

      // Restore return type when we leave this function.
      currentReturnType = previousReturnType;
      return null;
   }


   // -----------------------------------------------------------------------
   // RULE 4 — return statement must match the function's declared return type
   // -----------------------------------------------------------------------
   @Override
   public Void visitReturnStmt(Absyn.ReturnStmt node) {
      // First, visit the return expression so its typeAnnotation is set.
      super.visitReturnStmt(node);

      // Guard: a return can only appear inside a function.
      if (currentReturnType == null) {
         throw new TypeCheckException("Return statement found outside of a function!");
      }

      Type returnedType = node.expression.typeAnnotation;

      // The declared return type must be able to accept the actual returned type.
      if (!currentReturnType.canAccept(returnedType)) {
         throw new TypeCheckException(
            "Return type does not match the function's declared return type!");
      }

      return null;
   }


   // -----------------------------------------------------------------------
   // RULE 3 — function call: argument types must match parameter types,
   //          and the expression takes on the function's return type.
   // -----------------------------------------------------------------------
   @Override
   public Void visitFunExp(Absyn.FunExp node) {
      // Visit children first so every argument gets its typeAnnotation set.
      super.visitFunExp(node);

      // The function name must be a plain identifier.
      if (!(node.name instanceof Absyn.ID)) {
         throw new TypeCheckException("Function call name must be an identifier!");
      }
      String funcName = ((Absyn.ID) node.name).value;

      if (funcName.equals("printf")) {
         if (node.params.list.isEmpty()) {
            throw new TypeCheckException("printf requires at least one argument!");
         }
         if (!(node.params.list.get(0).typeAnnotation instanceof STRING)) {
            throw new TypeCheckException("printf's first argument must be a string!");
         }
         node.typeAnnotation = new VOID();
         return null;
      }

      if (funcName.equals("writeToFile")) {
         if (node.params.list.size() != 2) {
            throw new TypeCheckException("writeToFile expects exactly 2 arguments!");
         }
         if (!(node.params.list.get(0).typeAnnotation instanceof STRING) ||
             !(node.params.list.get(1).typeAnnotation instanceof STRING)) {
            throw new TypeCheckException("writeToFile expects string path and string content!");
         }
         node.typeAnnotation = new VOID();
         return null;
      }

      if (funcName.equals("readFromFile")) {
         if (node.params.list.size() != 1) {
            throw new TypeCheckException("readFromFile expects exactly 1 argument!");
         }
         if (!(node.params.list.get(0).typeAnnotation instanceof STRING)) {
            throw new TypeCheckException("readFromFile expects a string path!");
         }
         node.typeAnnotation = new STRING();
         return null;
      }

      if (funcName.equals("input")) {
         if (node.params.list.size() > 1) {
            throw new TypeCheckException("input expects 0 or 1 arguments!");
         }
         if (node.params.list.size() == 1 &&
             !(node.params.list.get(0).typeAnnotation instanceof INT)) {
            throw new TypeCheckException("input(target) requires an int target!");
         }
         node.typeAnnotation = new INT();
         return null;
      }

      // The identifier must refer to a known function in scope.
      if (!currentscope.hasFun(funcName)) {
         throw new TypeCheckException("Undefined function: " + funcName);
      }
      FunSymbol funSym = currentscope.getFun(funcName);

      // --- check argument count ---
      int expectedCount = funSym.params.typelist.size();
      int actualCount   = node.params.list.size();
      if (expectedCount != actualCount) {
         throw new TypeCheckException(
            "Function '" + funcName + "' expects " + expectedCount +
            " argument(s) but received " + actualCount + "!");
      }

      // --- check each argument type against the matching parameter type ---
      for (int i = 0; i < actualCount; i++) {
         Type expectedType = funSym.params.typelist.get(i);
         Type actualType   = node.params.list.get(i).typeAnnotation;

         if (!expectedType.canAccept(actualType)) {
            throw new TypeCheckException(
               "Argument " + (i + 1) + " of function '" + funcName +
               "' has the wrong type!");
         }
      }

      // The type of the whole call expression is the function's return type.
      node.typeAnnotation = funSym.returnType;
      return null;
   }


   // -----------------------------------------------------------------------
   // RULES 5, 9, 10 — expression list / initializer
   //
   // An ExpList like { expr1, expr2, expr3 } is used as an array, struct,
   // or union initializer.  We visit each element and then build a LIST
   // type from their individual types.  Rule 1 (visitVarDecl) will later
   // call canAccept to verify the initializer matches the declared variable
   // type — the LIST/OR canAccept logic handles structs and unions for us.
   // -----------------------------------------------------------------------
   @Override
   public Void visitExpList(Absyn.ExpList node) {
      // Visit every expression inside the list first.
      super.visitExpList(node);

      // Collect the type of each element.
      ArrayList<Type> elementTypes = new ArrayList<>();
      for (Absyn.Exp e : node.list) {
         elementTypes.add(e.typeAnnotation);
      }

      // The whole initializer list has type LIST(element types).
      node.typeAnnotation = new LIST(elementTypes);
      return null;
   }


   // -----------------------------------------------------------------------
   // RULES 6, 7 — array / list indexing
   //
   // For an expression like  a[i][j]:
   //   - Each index must be an INT.
   //   - Each [] peels one layer off the array/list type.
   //   - The final typeAnnotation is the innermost element type.
   // -----------------------------------------------------------------------
   @Override
   public Void visitArrayExp(Absyn.ArrayExp node) {
      // Visit the array name and all index expressions first.
      super.visitArrayExp(node);

      // Start from the type of the array/list being indexed.
      Type currentType = node.name.typeAnnotation;

      // Process each index dimension in order.
      for (Absyn.Exp idx : node.index_list.list) {

         // --- Rule 6: every index must be an integer ---
         Type idxType = idx.typeAnnotation;
         if (!(idxType instanceof INT)) {
            throw new TypeCheckException("Array index must be an integer!");
         }

         // --- Rule 7: peel one layer off the collection type ---
         if (currentType instanceof ARRAY) {
            // Unsized array: ARRAY(elementType) → elementType
            currentType = ((ARRAY) currentType).type;

         } else if (currentType instanceof LIST) {
            // Sized array: LIST(t, t, t, ...) → the shared element type
            // All elements of a well-formed sized array have the same type,
            // so we just take the first one.
            LIST list = (LIST) currentType;
            if (list.typelist.isEmpty()) {
               throw new TypeCheckException("Cannot index into an empty list!");
            }
            currentType = list.typelist.get(0);

         } else {
            // Cannot index into a non-array type.
            throw new TypeCheckException(
               "Cannot use [] on a non-array type!");
         }
      }

      // The final type after all indices are applied.
      node.typeAnnotation = currentType;
      return null;
   }

}
