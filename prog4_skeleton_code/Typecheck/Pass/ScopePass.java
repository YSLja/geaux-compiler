package Typecheck.Pass;
import Absyn.*;
import Typecheck.SymbolTable.*;

public class ScopePass<T> extends Pass<T> {

   protected Scope currentscope;
	protected T defaultReturn = null;

    // Hint: Save scope → switch to node.scope → visit children → restore scope.

   public ScopePass(Scope s) {
      this.currentscope = s;
   }

   @Override
   public T visitFunDecl(FunDecl node) {
        Scope previous = this.currentscope;
        this.currentscope = node.scope;
        T ret = super.visitFunDecl(node);
        this.currentscope = previous;
        return ret;
   }

   @Override
	public T visitStructDecl(StructDecl node) {
        Scope previous = this.currentscope;
        this.currentscope = node.scope;
        T ret = super.visitStructDecl(node);
        this.currentscope = previous;
        return ret;
	}

	@Override
	public T visitUnionDecl(UnionDecl node) {
        Scope previous = this.currentscope;
        this.currentscope = node.scope;
        T ret = super.visitUnionDecl(node);
        this.currentscope = previous;
        return ret;
	}

	@Override
	public T visitIfStmt(IfStmt node) {
        Scope previous = this.currentscope;
        this.currentscope = node.scope;
        T ret = super.visitIfStmt(node);
        this.currentscope = previous;
        return ret;
	}

   @Override
	public T visitWhileStmt(WhileStmt node) {
        Scope previous = this.currentscope;
        this.currentscope = node.scope;
        T ret = super.visitWhileStmt(node);
        this.currentscope = previous;
        return ret;
	}

}
