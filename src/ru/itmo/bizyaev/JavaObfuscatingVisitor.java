package ru.itmo.bizyaev;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.*;
import ru.itmo.bizyaev.generated.JavaBasicParser;
import ru.itmo.bizyaev.generated.JavaBasicVisitor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides an empty implementation of {@link JavaBasicVisitor},
 * which can be extended to create a visitor which only needs to handle a subset
 * of the available methods.
 */
public class JavaObfuscatingVisitor extends AbstractParseTreeVisitor<String> implements JavaBasicVisitor<String> {

    // Map of all identifier (var name??) replacements, str -> str
    private Map<String, String> idReplacementMap;
    // Stack of lists of vars to delete after the end of the current scope
    private Deque<ArrayList<String>> scopeCleanupStack;
    // last generated obfuscated variable name
    private String lastIdReplacement;
    // current indentation level (changes with scope entry / quit)
    private int currentIndentLevel;

    JavaObfuscatingVisitor() {
        idReplacementMap = new HashMap<>();
        scopeCleanupStack = new ArrayDeque<>();
        lastIdReplacement = "I0100";
        currentIndentLevel = 0;
    }


    @Override
    protected String aggregateResult(String aggregate, String nextResult) {
        return aggregate + nextResult;
    }

    @Override
    public String visitMethodDeclaration(JavaBasicParser.MethodDeclarationContext ctx) {
        pushScope();
        String type = ctx.typeType() != null ? visit(ctx.typeType()) : "void";
        String contents = ctx.block() != null ? " " + visit(ctx.block()) : ";";
        String result = type + " " + ctx.IDENTIFIER().getText() + visit(ctx.formalParameters())
                 + visit(ctx.optionalBrackets()) + contents;
        popScope();
        return result;
    }

    @Override
    public String visitConstructorDeclaration(JavaBasicParser.ConstructorDeclarationContext ctx) {
        pushScope();
        String result = ctx.IDENTIFIER().getText() + " " + visit(ctx.formalParameters()) + visit(ctx.block());
        popScope();
        return result;
    }

    @Override
    public String visitBlock(JavaBasicParser.BlockContext ctx) {
        currentIndentLevel++;
        pushScope();
        String contents = ctx.blockStatement().stream().map((x) -> indent() + visit(x)).collect(Collectors.joining("\n"));
        popScope();
        currentIndentLevel--;
        return "{\n" + contents + "\n" + indent() + "}";
    }

    @Override
    public String visitCompilationUnit(JavaBasicParser.CompilationUnitContext ctx) {
        return ctx.typeDeclaration().stream().map(this::visit).collect(Collectors.joining("\n"));
    }

    @Override
    public String visitVariableDeclaratorId(JavaBasicParser.VariableDeclaratorIdContext ctx) {
        return replaceId(ctx.IDENTIFIER(), true) + visit(ctx.optionalBrackets());
    }

    @Override
    public String visitVariableInitializer(JavaBasicParser.VariableInitializerContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public String visitTypeDeclaration(JavaBasicParser.TypeDeclarationContext ctx) {
        String mods = ctx.modifier().stream().map(RuleContext::getText).collect(Collectors.joining(" "));
        String classDeclaration = visit(ctx.classDeclaration());
        return mods + classDeclaration + ";";
    }

    @Override
    public String visitClassDeclaration(JavaBasicParser.ClassDeclarationContext ctx) {
        return "class " + ctx.IDENTIFIER().getText() + visit(ctx.classBody());
    }

    @Override
    public String visitClassBody(JavaBasicParser.ClassBodyContext ctx) {
        currentIndentLevel++;
        String body = ctx.classBodyDeclaration().stream().map((x) -> indent() + visit(x)).collect(Collectors.joining("\n"));
        currentIndentLevel--;
        return " {\n" + body + "\n" + indent() + "}";
    }

    @Override
    public String visitEmptyClassBodyDecl(JavaBasicParser.EmptyClassBodyDeclContext ctx) { return ";"; }

    @Override
    public String visitBlockClassBodyDecl(JavaBasicParser.BlockClassBodyDeclContext ctx) {
        String staticMod = ctx.STATIC() == null ? "" : ctx.STATIC().getText() + " ";
        return staticMod + visit(ctx.block());
    }

    @Override
    public String visitMemberClassBodyDecl(JavaBasicParser.MemberClassBodyDeclContext ctx) {
        String mods = ctx.modifier().stream().map(RuleContext::getText).collect(Collectors.joining(" "));
        return mods + (ctx.modifier().size() > 0 ? " " : "") + visit(ctx.memberDeclaration());
    }

    @Override
    public String visitMemberDeclaration(JavaBasicParser.MemberDeclarationContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public String visitFieldDeclaration(JavaBasicParser.FieldDeclarationContext ctx) {
        return visit(ctx.typeType()) + " " + visit(ctx.variableDeclarators()) + ";";
    }

    @Override
    public String visitVariableDeclarators(JavaBasicParser.VariableDeclaratorsContext ctx) {
        return ctx.variableDeclarator().stream().map(this::visitVariableDeclarator).collect(Collectors.joining(", "));
    }

    @Override
    public String visitVariableDeclarator(JavaBasicParser.VariableDeclaratorContext ctx) {
        String varInit = ctx.variableInitializer() == null ? "" : (" = " + visit(ctx.variableInitializer()));
        return visit(ctx.variableDeclaratorId()) + varInit;
    }

    @Override
    public String visitArrayInitializer(JavaBasicParser.ArrayInitializerContext ctx) {
        String varInits = ctx.variableInitializer().stream().map(this::visitVariableInitializer).collect(Collectors.joining(", "));
        return "{" + varInits + "}";
    }

    @Override
    public String visitFormalParameters(JavaBasicParser.FormalParametersContext ctx) {
        String paramList = ctx.formalParameterList() == null ? "" : visit(ctx.formalParameterList());
        return "(" + paramList + ")";
    }

    @Override
    public String visitFormalParameterList(JavaBasicParser.FormalParameterListContext ctx) {
        return ctx.formalParameter().stream().map(this::visitFormalParameter).collect(Collectors.joining(", "));
    }

    @Override
    public String visitFormalParameter(JavaBasicParser.FormalParameterContext ctx) {
        String finalMod = ctx.FINAL() == null ? "" : "final ";
        return finalMod + visit(ctx.typeType()) + " " + visit(ctx.variableDeclaratorId());
    }

    @Override
    public String visitVarDeclBlockStatement(JavaBasicParser.VarDeclBlockStatementContext ctx) {
        String finalMod = ctx.FINAL() == null ? "" : "final ";
        return finalMod + visit(ctx.typeType()) + " " + visit(ctx.variableDeclarators()) + ";";
    }

    @Override
    public String visitStatementBlockStatement(JavaBasicParser.StatementBlockStatementContext ctx) {
        return visit(ctx.statement());
    }

    @Override
    public String visitTypeDeclBlockStatement(JavaBasicParser.TypeDeclBlockStatementContext ctx) {
        return visit(ctx.typeDeclaration());
    }

    @Override
    public String visitNewBlockStatement(JavaBasicParser.NewBlockStatementContext ctx) {
        return visit(ctx.block());
    }

    @Override
    public String visitIfStatement(JavaBasicParser.IfStatementContext ctx) {
        String failBranch = ctx.failBranch == null ? "" : "else " + visit(ctx.failBranch);
        return "if (" + visit(ctx.expression()) + ") " + visit(ctx.succBranch) + failBranch;
    }

    @Override
    public String visitExpressionStatement(JavaBasicParser.ExpressionStatementContext ctx) {
        String ret = ctx.RETURN() == null ? "" : "return";
        String exp = ctx.expression() != null ? visit(ctx.expression()) : "";
        return ret + (!ret.isEmpty() && !exp.isEmpty() ? " " : "") + exp + ";";
    }

    @Override
    public String visitExpressionList(JavaBasicParser.ExpressionListContext ctx) {
        return ctx.expression().stream().map(this::visit).collect(Collectors.joining(", "));
    }

    @Override
    public String visitMethodCall(JavaBasicParser.MethodCallContext ctx) {
        String callee = ctx.IDENTIFIER() != null ? ctx.IDENTIFIER().getText() : "this";
        return callee + "(" + visit(ctx.expressionList()) + ")";
    }

    @Override
    public String visitPrimaryExpression(JavaBasicParser.PrimaryExpressionContext ctx) {
        return visit(ctx.primary());
    }

    @Override
    public String visitCastExpression(JavaBasicParser.CastExpressionContext ctx) {
        return "(" + visit(ctx.typeType()) + ") " + visit(ctx.expression());
    }

    @Override
    public String visitDotExpression(JavaBasicParser.DotExpressionContext ctx) {
        String afterDot = ctx.IDENTIFIER() != null ? ctx.IDENTIFIER().getText() : visit(ctx.methodCall());
        return visit(ctx.expression()) + "." + afterDot;
    }

    @Override
    public String visitSubscriptExpression(JavaBasicParser.SubscriptExpressionContext ctx) {
        return visit(ctx.ext) + "[" + visit(ctx.subscript) + "]";
    }

    @Override
    public String visitNewExpression(JavaBasicParser.NewExpressionContext ctx) {
        String toCreate = ctx.qualifiedName() == null ? visit(ctx.primitiveType()) : visit(ctx.qualifiedName());
        String rest = ctx.arrayCreatorRest() == null ? visit(ctx.classCreatorRest()) : visit(ctx.arrayCreatorRest());
        return "new " + toCreate + rest;
    }

    @Override
    public String visitMethodCallExpression(JavaBasicParser.MethodCallExpressionContext ctx) {
        return visit(ctx.methodCall());
    }

    @Override
    public String visitOpExpression(JavaBasicParser.OpExpressionContext ctx) {
        String prefix = ctx.prefix == null ? "" : ctx.prefix.getText();
        String postfix = ctx.postfix == null ? "" : ctx.postfix.getText();
        String bop = ctx.bop == null ? "" : " " + ctx.bop.getText() + " ";
        return prefix + ctx.expression().stream().map(this::visit).collect(Collectors.joining(bop)) + postfix;
    }

    @Override
    public String visitParenthesesPrimary(JavaBasicParser.ParenthesesPrimaryContext ctx) {
        return "(" + visit(ctx.expression()) + ")";
    }

    @Override
    public String visitThisPrimary(JavaBasicParser.ThisPrimaryContext ctx) {
        return "this";
    }

    @Override
    public String visitLiteralPrimary(JavaBasicParser.LiteralPrimaryContext ctx) {
        return ctx.literal().getText();
    }

    @Override
    public String visitIdPrimary(JavaBasicParser.IdPrimaryContext ctx) {
        return replaceId(ctx.IDENTIFIER(), false);
    }

    @Override
    public String visitInitArrayCreatorRest(JavaBasicParser.InitArrayCreatorRestContext ctx) {
        String brackets = new String(new char[ctx.LBRACK().size()]).replace("\0", "[]");
        return brackets + visit(ctx.arrayInitializer());
    }

    @Override
    public String visitExprArrayCreatorRest(JavaBasicParser.ExprArrayCreatorRestContext ctx) {
        String expressions = ctx.expression().stream().map((x) -> "[" + visit(x) + "]").collect(Collectors.joining());
        return expressions + visit(ctx.optionalBrackets());
    }

    @Override
    public String visitClassCreatorRest(JavaBasicParser.ClassCreatorRestContext ctx) {
        String expList = ctx.expressionList() == null ? "" : visit(ctx.expressionList());
        return "(" + expList + ")" + visit(ctx.classBody());
    }

    @Override
    public String visitTypeType(JavaBasicParser.TypeTypeContext ctx) {
        String obj = ctx.qualifiedName() != null ? visit(ctx.qualifiedName()) : visit(ctx.primitiveType());
        return obj + visit(ctx.optionalBrackets());
    }

    @Override
    public String visitQualifiedName(JavaBasicParser.QualifiedNameContext ctx) {
        return ctx.getText();
    }

    @Override
    public String visitOptionalBrackets(JavaBasicParser.OptionalBracketsContext ctx) {
        return new String(new char[ctx.getChildCount() / 2]).replace("\0", "[]");
    }

    @Override
    public String visitLiteral(JavaBasicParser.LiteralContext ctx) {
        return ctx.getText();
    }

    @Override
    public String visitModifier(JavaBasicParser.ModifierContext ctx) {
        return ctx.getText();
    }

    @Override
    public String visitPrimitiveType(JavaBasicParser.PrimitiveTypeContext ctx) {
        return ctx.getText();
    }

    // a function to generate next obfuscated var name (can increment the property)
    private String nextIdReplacement() {
        StringBuilder newIdReplacement = new StringBuilder();
        int i = lastIdReplacement.length() - 1;
        boolean incrementSucceeded = false;
        // Our "digits" are 0, 1, O, I
        for (; i >= 0; --i) {
            switch (lastIdReplacement.charAt(i)) {
                case '0':
                    newIdReplacement.append('1');
                    incrementSucceeded = true;
                    break;
                case 'O':
                    newIdReplacement.append('I');
                    incrementSucceeded = true;
                    break;
                case '1':
                    newIdReplacement.append('O');
                    incrementSucceeded = true;
                    break;
                case 'I':
                    if (i == 0) {
                        newIdReplacement.append("0O");
                        incrementSucceeded = true;
                    } else {
                        newIdReplacement.append("0");
                    }
            }
            if (incrementSucceeded) break;
        }
        lastIdReplacement = lastIdReplacement.substring(0, i) + newIdReplacement.reverse().toString();
        return lastIdReplacement;
    }

    private String indent() {
        final int FORMAT_IDENT_SPACES = 4;

        return new String(new char[FORMAT_IDENT_SPACES * currentIndentLevel]).replace('\0', ' ');
    }

    private void pushScope() { scopeCleanupStack.push(new ArrayList<>()); }

    private String replaceId(TerminalNode identifier, boolean canCreateNew) {
        String varName = identifier.getText();
        if (idReplacementMap.containsKey(varName)) {
            return idReplacementMap.get(varName);
        } else if (canCreateNew) {
            String replacement = nextIdReplacement();
            idReplacementMap.put(varName, replacement);
            scopeCleanupStack.getLast().add(varName);
            return replacement;
        } else {
            return varName;
        }
    }

    private void popScope() { idReplacementMap.keySet().removeAll(scopeCleanupStack.pop()); }
}