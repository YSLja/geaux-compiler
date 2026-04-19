package Typecheck.Pass;
import Typecheck.SymbolTable.*;
import Typecheck.TypeCheckException;
import Absyn.*;

public class CreateScopePass extends Pass<Void> {

   protected Scope currentscope;
   public Scope globalscope;

   public CreateScopePass() {
      this.globalscope = new Scope();
      this.currentscope = globalscope;
   }
// Hint: Functions introduce a new nested scope.
// 1. Create a new Scope whose parent is the current scope.
// 2. Temporarily switch currentscope to this new scope.
// 3. Visit the function type, parameters, and body.
// 4. Store the resulting scope in node.scope.
// 5. Restore the previous scope.
   @Override
   public Void visitFunDecl(FunDecl node) {
      Scope previous = this.currentscope;
      this.currentscope = new Scope(previous);
      super.visitFunDecl(node);
      node.scope = this.currentscope;
      this.currentscope = previous;
      return null;
   }
// Hint: Struct bodies are evaluated inside their own scope.
// 1. Create a new Scope whose parent is the current scope.
// 2. Switch currentscope to this new scope.
// 3. Visit the struct body.
// 4. Store this scope in node.scope.
// 5. Restore the previous scope.
	@Override
	public Void visitStructDecl(StructDecl node) {
      Scope previous = this.currentscope;
      this.currentscope = new Scope(previous);
      super.visitStructDecl(node);
      node.scope = this.currentscope;
      this.currentscope = previous;
      return null;
	}
// Hint: Union bodies behave like structs for scoping.
// 1. Create a new Scope whose parent is the current scope.
// 2. Switch currentscope to the new scope.
// 3. Visit the union body.
// 4. Store this scope in node.scope.
// 5. Restore the previous scope.
	@Override
	public Void visitUnionDecl(UnionDecl node) {
      Scope previous = this.currentscope;
      this.currentscope = new Scope(previous);
      super.visitUnionDecl(node);
      node.scope = this.currentscope;
      this.currentscope = previous;
      return null;
	}
// Hint: If statements execute inside a fresh scope.
// 1. Create a new Scope whose parent is the current scope.
// 2. Switch currentscope to this new scope.
// 3. Visit the condition and both branches.
// 4. Save this scope in node.scope.
// 5. Restore the previous scope.
	@Override
	public Void visitIfStmt(IfStmt node) {
      Scope previous = this.currentscope;
      this.currentscope = new Scope(previous);
      super.visitIfStmt(node);
      node.scope = this.currentscope;
      this.currentscope = previous;
      return null;
	}
// Hint: Loops also introduce a nested scope.
// 1. Create a new Scope whose parent is the current scope.
// 2. Switch currentscope to the new scope.
// 3. Visit the condition and loop body.
// 4. Store the scope in node.scope.
// 5. Restore the previous scope.
   @Override
	public Void visitWhileStmt(WhileStmt node) {
      Scope previous = this.currentscope;
      this.currentscope = new Scope(previous);
      super.visitWhileStmt(node);
      node.scope = this.currentscope;
      this.currentscope = previous;
      return null;
	}

}
