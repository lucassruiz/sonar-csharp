/*
 * Copyright (C) 2010 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.csharp.squid.parser;

import static com.sonar.csharp.squid.api.CSharpKeyword.*;
import static com.sonar.csharp.squid.api.CSharpPunctuator.*;
import static com.sonar.csharp.squid.api.CSharpTokenType.*;
import static com.sonar.sslr.api.GenericTokenType.*;
import static com.sonar.sslr.impl.matcher.Matchers.*;

import com.sonar.csharp.squid.api.CSharpGrammar;
import com.sonar.sslr.api.GrammarDecorator;
import com.sonar.sslr.impl.GrammarRuleLifeCycleManager;

/**
 * Definition of each element of the C# grammar.
 */
public class CSharpGrammarDecorator implements GrammarDecorator<CSharpGrammar> {

  private static final String SET = "set";
  private static final String GET = "get";
  private static final String PARTIAL = "partial";

  /**
   * ${@inheritDoc}
   */
  public void decorate(CSharpGrammar g) {
    GrammarRuleLifeCycleManager.initializeRuleFields(g, CSharpGrammar.class);

    // We follow the C# language specification 4.0
    g.literal.isOr(TRUE, FALSE, INTEGER_DEC_LITERAL, INTEGER_HEX_LITERAL, REAL_LITERAL, CHARACTER_LITERAL, STRING_LITERAL, NULL);
    g.rightShift.is(SUPERIOR, SUPERIOR);
    g.rightShiftAssignment.is(SUPERIOR, GE_OP);

    // A.2.1 Basic concepts
    basicConcepts(g);

    // A.2.2 Types
    types(g);

    // A.2.3 Variables
    variables(g);

    // A.2.4 Expressions
    expressions(g);

    // A.2.5 Statements
    statements(g);

    // A.2.6 Classes
    classes(g);

    // A.2.7 Struct
    structs(g);

    // A.2.8 Arrays
    arrays(g);

    // A.2.9 Interfaces
    interfaces(g);

    // A.2.10 Enums
    enums(g);

    // A.2.11 Delegates
    delegates(g);

    // A.2.12 Attributes
    attributes(g);

    // A.2.13 Generics
    generics(g);

    // A.3 Unsafe code
    unsafe(g);

  }

  private void basicConcepts(CSharpGrammar g) {
    g.compilationUnit.is(o2n(g.externAliasDirective), o2n(g.usingDirective), opt(g.globalAttributes), o2n(g.namespaceMemberDeclaration),
        EOF);
    g.namespaceName.is(g.namespaceOrTypeName);
    g.typeName.is(g.namespaceOrTypeName);
    g.namespaceOrTypeName.is(
        or(
            g.qualifiedAliasMember,
            and(IDENTIFIER, opt(g.typeArgumentList))
        ),
        o2n(
            DOT, IDENTIFIER, opt(g.typeArgumentList)
        ));
  }

  private void types(CSharpGrammar g) {
    g.simpleType.isOr(g.numericType, BOOL);
    g.numericType.isOr(g.integralType, g.floatingPointType, DECIMAL);
    g.integralType.isOr(SBYTE, BYTE, SHORT, USHORT, INT, UINT, LONG, ULONG, CHAR);
    g.floatingPointType.isOr(FLOAT, DOUBLE);

    g.rankSpecifier.is(LBRACKET, o2n(COMMA), RBRACKET);
    g.rankSpecifiers.is(one2n(g.rankSpecifier));

    g.typePrimary.is(
        or(
            g.simpleType,
            "dynamic",
            OBJECT,
            STRING,
            g.typeName
        )).skip();
    g.nullableType.is(g.typePrimary, adjacent(QUESTION));
    g.pointerType.is( // Moved from unsafe code to remove the left recursions
        or(
            g.nullableType,
            g.typePrimary,
            VOID
        ),
        STAR
        );
    g.arrayType.is(
        or(
            g.pointerType,
            g.nullableType,
            g.typePrimary
        ),
        g.rankSpecifiers
        );
    g.type.is(
        or(
            g.arrayType,
            g.pointerType,
            g.nullableType,
            g.typePrimary
        ));

    g.nonNullableValueType.is(not(g.nullableType), g.type);
    g.nonArrayType.is(not(g.arrayType), g.type);

    g.classType.isOr("dynamic", OBJECT, STRING, g.typeName);
    g.interfaceType.is(g.typeName);
    g.enumType.is(g.typeName);
    g.delegateType.is(g.typeName);
  }

  private void variables(CSharpGrammar g) {
    g.variableReference.is(g.expression);
  }

  private void expressions(CSharpGrammar g) {
    g.argumentList.is(g.argument, o2n(COMMA, g.argument));
    g.argument.is(opt(g.argumentName), g.argumentValue);
    g.argumentName.is(IDENTIFIER, COLON);
    g.argumentValue.isOr(g.expression, and(REF, g.variableReference), and(OUT, g.variableReference));
    g.primaryExpression.isOr(g.arrayCreationExpression, g.primaryNoArrayCreationExpression);
    g.primaryNoArrayCreationExpression.isOr(g.literal, g.simpleName, g.parenthesizedExpression, g.elementAccess, g.memberAccess,
        g.invocationExpression, g.thisAccess, g.baseAccess, g.postIncrementExpression, g.postDecrementExpression,
        g.objectCreationExpression, g.delegateCreationExpression, g.anonymousObjectCreationExpression, g.typeOfExpression,
        g.checkedExpression, g.uncheckedExpression, g.defaultValueExpression, g.anonymousMethodExpression);
    g.simpleName.is(IDENTIFIER, opt(g.typeArgumentList));
    g.parenthesizedExpression.is(LPARENTHESIS, g.expression, RPARENTHESIS);
    g.memberAccess.isOr(and(g.qualifiedAliasMember, DOT, IDENTIFIER),
        and(or(g.primaryExpression, g.predefinedType), DOT, IDENTIFIER, opt(g.typeArgumentList)));
    g.predefinedType.isOr(BOOL, BYTE, CHAR, DECIMAL, DOUBLE, FLOAT, INT, LONG, OBJECT, SBYTE, SHORT, STRING, UINT, ULONG, USHORT);
    g.invocationExpression.is(g.primaryExpression, LPARENTHESIS, opt(g.argumentList), RPARENTHESIS);
    g.elementAccess.is(g.primaryNoArrayCreationExpression, LBRACKET, g.argumentList, RBRACKET);
    g.thisAccess.is(THIS);
    // NOTE: g.baseAccess does not exactly stick to the specification: "opt(g.typeArgumentList)" has been added here, whereas it is not
    // present in the "base-access" rule in the specification of C# 4.0
    g.baseAccess.is(BASE, or(and(DOT, IDENTIFIER, opt(g.typeArgumentList)), and(LBRACKET, g.argumentList, RBRACKET)));
    g.postIncrementExpression.is(g.primaryExpression, INC_OP);
    g.postDecrementExpression.is(g.primaryExpression, DEC_OP);
    g.objectCreationExpression.is(NEW, g.type,
        or(and(LPARENTHESIS, opt(g.argumentList), RPARENTHESIS, opt(g.objectOrCollectionInitializer)), g.objectOrCollectionInitializer));
    g.objectOrCollectionInitializer.isOr(g.objectInitializer, g.collectionInitializer);
    g.objectInitializer.is(LCURLYBRACE, opt(g.memberInitializer), o2n(COMMA, g.memberInitializer), opt(COMMA), RCURLYBRACE);
    g.memberInitializer.is(IDENTIFIER, EQUAL, g.initializerValue);
    g.initializerValue.isOr(g.expression, g.objectOrCollectionInitializer);
    g.collectionInitializer.is(LCURLYBRACE, g.elementInitializer, o2n(COMMA, g.elementInitializer), opt(COMMA), RCURLYBRACE);
    g.elementInitializer.isOr(g.nonAssignmentExpression, and(LCURLYBRACE, g.expressionList, RCURLYBRACE));
    g.expressionList.is(g.expression, o2n(COMMA, g.expression));
    g.arrayCreationExpression.isOr(
        and(NEW, g.nonArrayType, LBRACKET, g.expressionList, RBRACKET, o2n(g.rankSpecifier), opt(g.arrayInitializer)),
        and(NEW, g.arrayType, g.arrayInitializer), and(NEW, g.rankSpecifier, g.arrayInitializer));
    g.delegateCreationExpression.is(NEW, g.delegateType, LPARENTHESIS, g.expression, RPARENTHESIS);
    g.anonymousObjectCreationExpression.is(NEW, g.anonymousObjectInitializer);
    g.anonymousObjectInitializer.is(LCURLYBRACE, opt(g.memberDeclarator), o2n(COMMA, g.memberDeclarator), opt(COMMA), RCURLYBRACE);
    g.memberDeclarator.isOr(g.memberAccess, and(IDENTIFIER, EQUAL, g.expression), g.simpleName);
    // NOTE : g.typeOfExpression does not exactly stick to the specification, but the bridge makes its easier to parse for now.
    // g.typeOfExpression.is(TYPEOF, LPARENTHESIS, or(g.type, g.unboundTypeName, VOID), RPARENTHESIS);
    g.typeOfExpression.is(TYPEOF, bridge(LPARENTHESIS, RPARENTHESIS));
    g.unboundTypeName.is(
        one2n(IDENTIFIER, opt(DOUBLE_COLON, IDENTIFIER), opt(g.genericDimensionSpecifier),
            opt(DOT, IDENTIFIER, opt(g.genericDimensionSpecifier))), opt(DOT, IDENTIFIER, opt(g.genericDimensionSpecifier)));
    g.genericDimensionSpecifier.is(INFERIOR, o2n(COMMA), SUPERIOR);
    g.checkedExpression.is(CHECKED, LPARENTHESIS, g.expression, RPARENTHESIS);
    g.uncheckedExpression.is(UNCHECKED, LPARENTHESIS, g.expression, RPARENTHESIS);
    g.defaultValueExpression.is(DEFAULT, LPARENTHESIS, g.type, RPARENTHESIS);
    g.unaryExpression.isOr(g.castExpression, g.primaryExpression, and(or(PLUS, MINUS, EXCLAMATION, TILDE), g.unaryExpression),
        g.preIncrementExpression, g.preDecrementExpression);
    g.preIncrementExpression.is(INC_OP, g.unaryExpression);
    g.preDecrementExpression.is(DEC_OP, g.unaryExpression);
    g.castExpression.is(LPARENTHESIS, g.type, RPARENTHESIS, g.unaryExpression);
    g.multiplicativeExpression.is(g.unaryExpression, o2n(or(STAR, SLASH, MODULO), g.unaryExpression));
    g.additiveExpression.is(g.multiplicativeExpression, o2n(or(PLUS, MINUS), g.multiplicativeExpression));
    g.shiftExpression.is(g.additiveExpression, o2n(or(LEFT_OP, g.rightShift), g.additiveExpression));
    g.relationalExpression.is(g.shiftExpression,
        o2n(or(and(or(INFERIOR, SUPERIOR, LE_OP, GE_OP), g.shiftExpression), and(or(IS, AS), g.type))));
    g.equalityExpression.is(g.relationalExpression, o2n(or(EQ_OP, NE_OP), g.relationalExpression));
    g.andExpression.is(g.equalityExpression, o2n(AND, g.equalityExpression));
    g.exclusiveOrExpression.is(g.andExpression, o2n(XOR, g.andExpression));
    g.inclusiveOrExpression.is(g.exclusiveOrExpression, o2n(OR, g.exclusiveOrExpression));
    g.conditionalAndExpression.is(g.inclusiveOrExpression, o2n(AND_OP, g.inclusiveOrExpression));
    g.conditionalOrExpression.is(g.conditionalAndExpression, o2n(OR_OP, g.conditionalAndExpression));
    g.nullCoalescingExpression.is(g.conditionalOrExpression, opt(DOUBLE_QUESTION, g.nullCoalescingExpression));
    g.conditionalExpression.is(g.nullCoalescingExpression, opt(QUESTION, g.expression, COLON, g.expression));
    g.lambdaExpression.is(g.anonymousFunctionSignature, LAMBDA, g.anonymousFunctionBody);
    g.anonymousMethodExpression.is(DELEGATE, opt(g.explicitAnonymousFunctionSignature), g.block);
    g.anonymousFunctionSignature.isOr(g.explicitAnonymousFunctionSignature, g.implicitAnonymousFunctionSignature);
    g.explicitAnonymousFunctionSignature.is(LPARENTHESIS,
        opt(g.explicitAnonymousFunctionParameter, o2n(COMMA, g.explicitAnonymousFunctionParameter)), RPARENTHESIS);
    g.explicitAnonymousFunctionParameter.is(opt(g.anonymousFunctionParameterModifier), g.type, IDENTIFIER);
    g.anonymousFunctionParameterModifier.isOr("ref", "out");
    g.implicitAnonymousFunctionSignature.isOr(g.implicitAnonymousFunctionParameter,
        and(LPARENTHESIS, opt(g.implicitAnonymousFunctionParameter, o2n(COMMA, g.implicitAnonymousFunctionParameter)), RPARENTHESIS));
    g.implicitAnonymousFunctionParameter.is(IDENTIFIER);
    g.anonymousFunctionBody.isOr(g.expression, g.block);
    g.queryExpression.is(g.fromClause, g.queryBody);
    g.fromClause.is("from", or(and(g.type, IDENTIFIER), IDENTIFIER), IN, g.expression);
    g.queryBody.is(o2n(g.queryBodyClause), g.selectOrGroupClause, opt(g.queryContinuation));
    g.queryBodyClause.isOr(g.fromClause, g.letClause, g.whereClause, g.joinIntoClause, g.joinClause, g.orderByClause);
    g.letClause.is("let", IDENTIFIER, EQUAL, g.expression);
    g.whereClause.is("where", g.booleanExpression);
    g.joinClause.is("join", or(and(g.type, IDENTIFIER), IDENTIFIER), IN, g.expression, "on", g.expression, "equals", g.expression);
    g.joinIntoClause.is("join", or(and(g.type, IDENTIFIER), IDENTIFIER), IN, g.expression, "on", g.expression, "equals", g.expression,
        "into", IDENTIFIER);
    g.orderByClause.is("orderby", g.ordering, o2n(COMMA, g.ordering));
    g.ordering.is(g.expression, opt(g.orderingDirection));
    g.orderingDirection.isOr("ascending", "descending");
    g.selectOrGroupClause.isOr(g.selectClause, g.groupClause);
    g.selectClause.is("select", g.expression);
    g.groupClause.is("group", g.expression, "by", g.expression);
    g.queryContinuation.is("into", IDENTIFIER, g.queryBody);
    g.assignment.is(
        g.unaryExpression,
        or(EQUAL, ADD_ASSIGN, SUB_ASSIGN, MUL_ASSIGN, DIV_ASSIGN, MOD_ASSIGN, AND_ASSIGN, OR_ASSIGN, XOR_ASSIGN, LEFT_ASSIGN,
            g.rightShiftAssignment), g.expression);
    g.expression.isOr(g.assignment, g.nonAssignmentExpression);
    g.nonAssignmentExpression.isOr(g.lambdaExpression, g.queryExpression, g.conditionalExpression);
    g.constantExpression.is(g.expression);
    g.booleanExpression.is(g.expression);
  }

  private void statements(CSharpGrammar g) {
    g.statement.isOr(g.labeledStatement, g.declarationStatement, g.embeddedStatement);
    g.embeddedStatement.isOr(g.block, SEMICOLON, g.expressionStatement, g.selectionStatement, g.iterationStatement, g.jumpStatement,
        g.tryStatement, g.checkedStatement, g.uncheckedStatement, g.lockStatement, g.usingStatement, g.yieldStatement);
    g.block.is(LCURLYBRACE, o2n(g.statement), RCURLYBRACE);
    g.labeledStatement.is(IDENTIFIER, COLON, g.statement);
    g.declarationStatement.is(or(g.localVariableDeclaration, g.localConstantDeclaration), SEMICOLON);
    g.localVariableDeclaration.is(g.type, g.localVariableDeclarator, o2n(COMMA, g.localVariableDeclarator));
    g.localVariableDeclarator.is(IDENTIFIER, opt(EQUAL, g.localVariableInitializer));
    g.localVariableInitializer.isOr(g.expression, g.arrayInitializer);
    g.localConstantDeclaration.is(CONST, g.type, g.constantDeclarator, o2n(COMMA, g.constantDeclarator));
    g.constantDeclarator.is(IDENTIFIER, EQUAL, g.constantExpression);
    g.expressionStatement.is(g.statementExpression, SEMICOLON);
    g.statementExpression.isOr(g.postIncrementExpression, g.postDecrementExpression, g.preIncrementExpression, g.preDecrementExpression,
        g.assignment, g.invocationExpression, g.objectCreationExpression);
    g.selectionStatement.isOr(g.ifStatement, g.switchStatement);
    g.ifStatement.is(IF, LPARENTHESIS, g.booleanExpression, RPARENTHESIS, g.embeddedStatement, opt(ELSE, g.embeddedStatement));
    g.switchStatement.is(SWITCH, LPARENTHESIS, g.expression, RPARENTHESIS, LCURLYBRACE, o2n(g.switchSection), RCURLYBRACE);
    g.switchSection.is(one2n(g.switchLabel), one2n(g.statement));
    g.switchLabel.isOr(and(CASE, g.constantExpression, COLON), and(DEFAULT, COLON));
    g.iterationStatement.isOr(g.whileStatement, g.doStatement, g.forStatement, g.foreachStatement);
    g.whileStatement.is(WHILE, LPARENTHESIS, g.booleanExpression, RPARENTHESIS, g.embeddedStatement);
    g.doStatement.is(DO, g.embeddedStatement, WHILE, LPARENTHESIS, g.booleanExpression, RPARENTHESIS, SEMICOLON);
    g.forStatement.is(FOR, LPARENTHESIS, opt(g.forInitializer), SEMICOLON, opt(g.forCondition), SEMICOLON, opt(g.forIterator),
        RPARENTHESIS, g.embeddedStatement);
    g.forInitializer.isOr(g.localVariableDeclaration, g.statementExpressionList);
    g.forCondition.is(g.booleanExpression);
    g.forIterator.is(g.statementExpressionList);
    g.statementExpressionList.is(g.statementExpression, o2n(COMMA, g.statementExpression));
    g.foreachStatement.is(FOREACH, LPARENTHESIS, g.type, IDENTIFIER, IN, g.expression, RPARENTHESIS, g.embeddedStatement);
    g.jumpStatement.isOr(g.breakStatement, g.continueStatement, g.gotoStatement, g.returnStatement, g.throwStatement);
    g.breakStatement.is(BREAK, SEMICOLON);
    g.continueStatement.is(CONTINUE, SEMICOLON);
    g.gotoStatement.is(GOTO, or(IDENTIFIER, and(CASE, g.constantExpression), DEFAULT), SEMICOLON);
    g.returnStatement.is(RETURN, opt(g.expression), SEMICOLON);
    g.throwStatement.is(THROW, opt(g.expression), SEMICOLON);
    g.tryStatement.is(TRY, g.block, or(and(opt(g.catchClauses), g.finallyClause), g.catchClauses));
    g.catchClauses.isOr(and(o2n(g.specificCatchClause), g.generalCatchClause), one2n(g.specificCatchClause));
    g.specificCatchClause.is(CATCH, LPARENTHESIS, g.classType, opt(IDENTIFIER), RPARENTHESIS, g.block);
    g.generalCatchClause.is(CATCH, g.block);
    g.finallyClause.is(FINALLY, g.block);
    g.checkedStatement.is(CHECKED, g.block);
    g.uncheckedStatement.is(UNCHECKED, g.block);
    g.lockStatement.is(LOCK, LPARENTHESIS, g.expression, RPARENTHESIS, g.embeddedStatement);
    g.usingStatement.is(USING, LPARENTHESIS, g.resourceAcquisition, RPARENTHESIS, g.embeddedStatement);
    g.resourceAcquisition.isOr(g.localVariableDeclaration, g.expression);
    g.yieldStatement.is("yield", or(and(RETURN, g.expression), BREAK), SEMICOLON);
    g.namespaceDeclaration.is(NAMESPACE, g.qualifiedIdentifier, g.namespaceBody, opt(SEMICOLON));
    g.qualifiedIdentifier.is(IDENTIFIER, o2n(DOT, IDENTIFIER));
    g.namespaceBody.is(LCURLYBRACE, o2n(g.externAliasDirective), o2n(g.usingDirective), o2n(g.namespaceMemberDeclaration), RCURLYBRACE);
    g.externAliasDirective.is(EXTERN, "alias", IDENTIFIER, SEMICOLON);
    g.usingDirective.isOr(g.usingAliasDirective, g.usingNamespaceDirective);
    g.usingAliasDirective.is(USING, IDENTIFIER, EQUAL, g.namespaceOrTypeName, SEMICOLON);
    g.usingNamespaceDirective.is(USING, g.namespaceName, SEMICOLON);
    g.namespaceMemberDeclaration.isOr(g.namespaceDeclaration, g.typeDeclaration);
    g.typeDeclaration.isOr(g.classDeclaration, g.structDeclaration, g.interfaceDeclaration, g.enumDeclaration, g.delegateDeclaration);
    g.qualifiedAliasMember.is(IDENTIFIER, DOUBLE_COLON, IDENTIFIER, opt(g.typeArgumentList));
  }

  private void classes(CSharpGrammar g) {
    g.classDeclaration.is(opt(g.attributes), o2n(g.classModifier), opt(PARTIAL), CLASS, IDENTIFIER, opt(g.typeParameterList),
        opt(g.classBase), opt(g.typeParameterConstraintsClauses), g.classBody, opt(SEMICOLON));
    g.classModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE, ABSTRACT, SEALED, STATIC);
    g.classBase.is(COLON, or(and(g.classType, COMMA, g.interfaceTypeList), g.classType, g.interfaceTypeList));
    g.interfaceTypeList.is(g.interfaceType, o2n(COMMA, g.interfaceType));
    g.classBody.is(LCURLYBRACE, o2n(g.classMemberDeclaration), RCURLYBRACE);
    g.classMemberDeclaration.isOr(g.constantDeclaration, g.fieldDeclaration, g.methodDeclaration, g.propertyDeclaration,
        g.eventDeclaration, g.indexerDeclaration, g.operatorDeclaration, g.constructorDeclaration, g.destructorDeclaration,
        g.staticConstructorDeclaration, g.typeDeclaration);
    g.constantDeclaration.is(opt(g.attributes), o2n(g.constantModifier), CONST, g.type, g.constantDeclarator,
        o2n(COMMA, g.constantDeclarator), SEMICOLON);
    g.constantModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE);
    g.fieldDeclaration.is(opt(g.attributes), o2n(g.fieldModifier), g.type, g.variableDeclarator, o2n(COMMA, g.variableDeclarator),
        SEMICOLON);
    g.fieldModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE, STATIC, READONLY, VOLATILE);
    g.variableDeclarator.is(IDENTIFIER, opt(EQUAL, g.variableInitializer));
    g.variableInitializer.isOr(g.expression, g.arrayInitializer);
    g.methodDeclaration.is(g.methodHeader, g.methodBody);
    g.methodHeader.is(opt(g.attributes), o2n(g.methodModifier), opt(PARTIAL), g.returnType, g.memberName, opt(g.typeParameterList),
        LPARENTHESIS, opt(g.formalParameterList), RPARENTHESIS, opt(g.typeParameterConstraintsClauses)).skip();
    g.methodModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE, STATIC, VIRTUAL, SEALED, OVERRIDE, ABSTRACT, EXTERN);
    g.returnType.isOr(g.type, VOID);
    // NOTE: g.memberName does not exactly stick to the specification (see page 462 of ECMA specification)
    // Normally it would be: g.memberName.isOr(and(g.interfaceType, DOT, IDENTIFIER), IDENTIFIER);
    g.memberName.is(o2n(or(g.qualifiedAliasMember, and(or(THIS, IDENTIFIER), opt(g.typeArgumentList))), DOT), or(THIS, IDENTIFIER),
        opt(g.typeArgumentList));
    g.methodBody.isOr(g.block, SEMICOLON);
    g.formalParameterList.isOr(and(g.fixedParameters, opt(COMMA, g.parameterArray)), g.parameterArray);
    g.fixedParameters.is(g.fixedParameter, o2n(COMMA, g.fixedParameter));
    g.fixedParameter.is(opt(g.attributes), opt(g.parameterModifier), g.type, IDENTIFIER, opt(EQUAL, g.expression));
    g.parameterModifier.isOr(REF, OUT, THIS);
    g.parameterArray.is(opt(g.attributes), PARAMS, g.arrayType, IDENTIFIER);
    g.propertyDeclaration.is(opt(g.attributes), o2n(g.propertyModifier), g.type, g.memberName, LCURLYBRACE, g.accessorDeclarations,
        RCURLYBRACE);
    g.propertyModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE, STATIC, VIRTUAL, SEALED, OVERRIDE, ABSTRACT, EXTERN);
    g.accessorDeclarations.isOr(and(g.getAccessorDeclaration, opt(g.setAccessorDeclaration)),
        and(g.setAccessorDeclaration, opt(g.getAccessorDeclaration)));
    g.getAccessorDeclaration.is(opt(g.attributes), o2n(g.accessorModifier), GET, g.accessorBody);
    g.setAccessorDeclaration.is(opt(g.attributes), o2n(g.accessorModifier), SET, g.accessorBody);
    g.accessorModifier.isOr(and(PROTECTED, INTERNAL), and(INTERNAL, PROTECTED), PROTECTED, INTERNAL, PRIVATE);
    g.accessorBody.isOr(g.block, SEMICOLON);
    g.eventDeclaration.is(
        opt(g.attributes),
        o2n(g.eventModifier),
        EVENT,
        g.type,
        or(and(g.variableDeclarator, o2n(COMMA, g.variableDeclarator), SEMICOLON),
            and(g.memberName, LCURLYBRACE, g.eventAccessorDeclarations, RCURLYBRACE)));
    g.eventModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE, STATIC, VIRTUAL, SEALED, OVERRIDE, ABSTRACT, EXTERN);
    g.eventAccessorDeclarations.isOr(and(g.addAccessorDeclaration, g.removeAccessorDeclaration),
        and(g.removeAccessorDeclaration, g.addAccessorDeclaration));
    g.addAccessorDeclaration.is(opt(g.attributes), "add", g.block);
    g.removeAccessorDeclaration.is(opt(g.attributes), "remove", g.block);
    g.indexerDeclaration.is(opt(g.attributes), o2n(g.indexerModifier), g.indexerDeclarator, LCURLYBRACE, g.accessorDeclarations,
        RCURLYBRACE);
    g.indexerModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE, STATIC, VIRTUAL, SEALED, OVERRIDE, ABSTRACT, EXTERN);
    // NOTE: g.indexerDeclarator does not exactly stick to the specification. Normally it would be:
    // g.indexerDeclarator.is(g.type, opt(g.interfaceType, DOT), THIS, LBRACKET, g.formalParameterList, RBRACKET);
    g.indexerDeclarator.is(g.type, o2n(or(g.qualifiedAliasMember, and(IDENTIFIER, opt(g.typeArgumentList))), DOT), THIS, LBRACKET,
        g.formalParameterList, RBRACKET);
    g.operatorDeclaration.is(opt(g.attributes), one2n(g.operatorModifier), g.operatorDeclarator, g.operatorBody);
    g.operatorModifier.isOr(PUBLIC, STATIC, EXTERN);
    g.operatorDeclarator.isOr(g.unaryOperatorDeclarator, g.binaryOperatorDeclarator, g.conversionOperatorDeclarator);
    g.unaryOperatorDeclarator.is(g.type, OPERATOR, g.overloadableUnaryOperator, LPARENTHESIS, g.type, IDENTIFIER, RPARENTHESIS);
    g.overloadableUnaryOperator.isOr(PLUS, MINUS, EXCLAMATION, TILDE, INC_OP, DEC_OP, TRUE, FALSE);
    g.binaryOperatorDeclarator.is(g.type, OPERATOR, g.overloadableBinaryOperator, LPARENTHESIS, g.type, IDENTIFIER, COMMA, g.type,
        IDENTIFIER, RPARENTHESIS);
    g.overloadableBinaryOperator.isOr(PLUS, MINUS, STAR, SLASH, MODULO, AND, OR, XOR, LEFT_OP, g.rightShift, EQ_OP, NE_OP, SUPERIOR,
        INFERIOR, GE_OP, LE_OP);
    g.conversionOperatorDeclarator.is(or(IMPLICIT, EXPLICIT), OPERATOR, g.type, LPARENTHESIS, g.type, IDENTIFIER, RPARENTHESIS);
    g.operatorBody.isOr(g.block, SEMICOLON);
    g.constructorDeclaration.is(opt(g.attributes), o2n(g.constructorModifier), g.constructorDeclarator, g.constructorBody);
    g.constructorModifier.isOr(PUBLIC, PROTECTED, INTERNAL, PRIVATE, EXTERN);
    g.constructorDeclarator.is(IDENTIFIER, LPARENTHESIS, opt(g.formalParameterList), RPARENTHESIS, opt(g.constructorInitializer));
    g.constructorInitializer.is(COLON, or(BASE, THIS), LPARENTHESIS, opt(g.argumentList), RPARENTHESIS);
    g.constructorBody.isOr(g.block, SEMICOLON);
    g.staticConstructorDeclaration.is(opt(g.attributes), g.staticConstructorModifiers, IDENTIFIER, LPARENTHESIS, RPARENTHESIS,
        g.staticConstructorBody);
    g.staticConstructorModifiers.isOr(and(opt(EXTERN), STATIC, not(next(EXTERN))), and(STATIC, opt(EXTERN)));
    g.staticConstructorBody.isOr(g.block, SEMICOLON);
    g.destructorDeclaration.is(opt(g.attributes), opt(EXTERN), TILDE, IDENTIFIER, LPARENTHESIS, RPARENTHESIS, g.destructorBody);
    g.destructorBody.isOr(g.block, SEMICOLON);
  }

  private void structs(CSharpGrammar g) {
    g.structDeclaration.is(opt(g.attributes), o2n(g.structModifier), opt(PARTIAL), STRUCT, IDENTIFIER, opt(g.typeParameterList),
        opt(g.structInterfaces), opt(g.typeParameterConstraintsClauses), g.structBody, opt(SEMICOLON));
    g.structModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE);
    g.structInterfaces.is(COLON, g.interfaceTypeList);
    g.structBody.is(LCURLYBRACE, o2n(g.structMemberDeclaration), RCURLYBRACE);
    g.structMemberDeclaration.isOr(g.constantDeclaration, g.fieldDeclaration, g.methodDeclaration, g.propertyDeclaration,
        g.eventDeclaration, g.indexerDeclaration, g.operatorDeclaration, g.constructorDeclaration, g.staticConstructorDeclaration,
        g.typeDeclaration);
  }

  private void arrays(CSharpGrammar g) {
    g.arrayInitializer.is(LCURLYBRACE, or(and(g.variableInitializerList, COMMA), opt(g.variableInitializerList)), RCURLYBRACE);
    g.variableInitializerList.is(g.variableInitializer, o2n(COMMA, g.variableInitializer));
  }

  private void interfaces(CSharpGrammar g) {
    g.interfaceDeclaration.is(opt(g.attributes), o2n(g.interfaceModifier), opt(PARTIAL), INTERFACE, IDENTIFIER,
        opt(g.variantTypeParameterList), opt(g.interfaceBase), opt(g.typeParameterConstraintsClauses), g.interfaceBody, opt(SEMICOLON));
    g.interfaceModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE);
    g.variantTypeParameterList.is(INFERIOR, g.variantTypeParameter, o2n(COMMA, g.variantTypeParameter), SUPERIOR);
    g.variantTypeParameter.is(opt(g.attributes), opt(g.varianceAnnotation), g.typeParameter);
    g.varianceAnnotation.isOr(IN, OUT);
    g.interfaceBase.is(COLON, g.interfaceTypeList);
    g.interfaceBody.is(LCURLYBRACE, o2n(g.interfaceMemberDeclaration), RCURLYBRACE);
    g.interfaceMemberDeclaration.isOr(g.interfaceMethodDeclaration, g.interfacePropertyDeclaration, g.interfaceEventDeclaration,
        g.interfaceIndexerDeclaration);
    g.interfaceMethodDeclaration.is(opt(g.attributes), opt(NEW), g.returnType, IDENTIFIER, opt(g.typeParameterList), LPARENTHESIS,
        opt(g.formalParameterList), RPARENTHESIS, opt(g.typeParameterConstraintsClauses), SEMICOLON);
    g.interfacePropertyDeclaration.is(opt(g.attributes), opt(NEW), g.type, IDENTIFIER, LCURLYBRACE, g.interfaceAccessors, RCURLYBRACE);
    g.interfaceAccessors.is(opt(g.attributes),
        or(and(GET, SEMICOLON, opt(g.attributes), SET), and(SET, SEMICOLON, opt(g.attributes), GET), GET, SET), SEMICOLON);
    g.interfaceEventDeclaration.is(opt(g.attributes), opt(NEW), EVENT, g.type, IDENTIFIER, SEMICOLON);
    g.interfaceIndexerDeclaration.is(opt(g.attributes), opt(NEW), g.type, THIS, LBRACKET, g.formalParameterList, RBRACKET, LCURLYBRACE,
        g.interfaceAccessors, RCURLYBRACE);
  }

  private void enums(CSharpGrammar g) {
    g.enumDeclaration.is(opt(g.attributes), o2n(g.enumModifier), ENUM, IDENTIFIER, opt(g.enumBase), g.enumBody, opt(SEMICOLON));
    g.enumBase.is(COLON, g.integralType);
    g.enumBody.is(LCURLYBRACE, or(and(g.enumMemberDeclarations, COMMA), opt(g.enumMemberDeclarations)), RCURLYBRACE);
    g.enumModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE);
    g.enumMemberDeclarations.is(g.enumMemberDeclaration, o2n(COMMA, g.enumMemberDeclaration));
    g.enumMemberDeclaration.is(opt(g.attributes), IDENTIFIER, opt(EQUAL, g.constantExpression));
  }

  private void delegates(CSharpGrammar g) {
    g.delegateDeclaration.is(opt(g.attributes), o2n(g.delegateModifier), DELEGATE, g.returnType, IDENTIFIER,
        opt(g.variantTypeParameterList), LPARENTHESIS, opt(g.formalParameterList), RPARENTHESIS, opt(g.typeParameterConstraintsClauses),
        SEMICOLON);
    g.delegateModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE);
  }

  private void attributes(CSharpGrammar g) {
    g.globalAttributes.is(one2n(g.globalAttributeSection));
    g.globalAttributeSection.is(LBRACKET, g.globalAttributeTargetSpecifier, g.attributeList, opt(COMMA), RBRACKET);
    g.globalAttributeTargetSpecifier.is(g.globalAttributeTarget, COLON);
    g.globalAttributeTarget.isOr("assembly", "module");
    g.attributes.is(one2n(g.attributeSection));
    g.attributeSection.is(LBRACKET, opt(g.attributeTargetSpecifier), g.attributeList, opt(COMMA), RBRACKET);
    g.attributeTargetSpecifier.is(g.attributeTarget, COLON);
    g.attributeTarget.isOr("field", "event", "method", "param", "property", RETURN, "type");
    g.attributeList.is(g.attribute, o2n(COMMA, g.attribute));
    g.attribute.is(g.attributeName, opt(g.attributeArguments));
    g.attributeName.is(g.typeName);
    // NOTE: g.attributeArguments does not exactly stick to the specification, as normally a positionalArgument can not appear after a
    // namedArgument (see page 469 of ECMA specification)
    g.attributeArguments.is(LPARENTHESIS,
        opt(or(g.namedArgument, g.positionalArgument), o2n(COMMA, or(g.namedArgument, g.positionalArgument))), RPARENTHESIS);
    g.positionalArgument.is(opt(g.argumentName), g.attributeArgumentExpression);
    g.namedArgument.is(IDENTIFIER, EQUAL, g.attributeArgumentExpression);
    g.attributeArgumentExpression.is(g.expression);
  }

  private void generics(CSharpGrammar g) {
    g.typeParameterList.is(INFERIOR, g.typeParameters, SUPERIOR);
    g.typeParameters.is(opt(g.attributes), g.typeParameter, o2n(COMMA, opt(g.attributes), g.typeParameter));
    g.typeParameter.is(IDENTIFIER);
    g.typeArgumentList.is(INFERIOR, g.typeArgument, o2n(COMMA, g.typeArgument), SUPERIOR);
    g.typeArgument.is(g.type);
    g.typeParameterConstraintsClauses.is(one2n(g.typeParameterConstraintsClause));
    g.typeParameterConstraintsClause.is("where", g.typeParameter, COLON, g.typeParameterConstraints);
    g.typeParameterConstraints.isOr(and(g.primaryConstraint, COMMA, g.secondaryConstraints, COMMA, g.constructorConstraint),
        and(g.primaryConstraint, COMMA, or(g.secondaryConstraints, g.constructorConstraint)),
        and(g.secondaryConstraints, COMMA, g.constructorConstraint), g.primaryConstraint, g.secondaryConstraints, g.constructorConstraint);
    g.primaryConstraint.isOr(g.classType, CLASS, STRUCT);
    g.secondaryConstraints.is(or(g.interfaceType, g.typeParameter), o2n(COMMA, or(g.interfaceType, g.typeParameter)));
    g.constructorConstraint.is(NEW, LPARENTHESIS, RPARENTHESIS);
  }

  private void unsafe(CSharpGrammar g) {
    g.classModifier.or(UNSAFE);
    g.structModifier.or(UNSAFE);
    g.interfaceModifier.or(UNSAFE);
    g.delegateModifier.or(UNSAFE);
    g.fieldModifier.or(UNSAFE);
    g.methodModifier.or(UNSAFE);
    g.propertyModifier.or(UNSAFE);
    g.eventModifier.or(UNSAFE);
    g.indexerModifier.or(UNSAFE);
    g.operatorModifier.or(UNSAFE);
    g.constructorModifier.or(UNSAFE);

    g.destructorDeclaration.or(opt(g.attributes), o2n(or(EXTERN, UNSAFE)), TILDE, IDENTIFIER, LPARENTHESIS, RPARENTHESIS, g.destructorBody);
    g.staticConstructorModifiers.override(o2n(or(EXTERN, UNSAFE)), STATIC, o2n(or(EXTERN, UNSAFE)));
    g.embeddedStatement.or(or(g.unsafeStatement, g.fixedStatement));
    g.unsafeStatement.is(UNSAFE, g.block);

    // pointerType was moved to the types part in order to remove the left recursions

    // NOTE : g.unsafe.pointerElementAccess deactivated here because it shadows the "g.elementAccess" in the main grammar...
    // Need to look into that later.
    g.primaryNoArrayCreationExpression.or(or(g.pointerMemberAccess, /* g.unsafe.pointerElementAccess, */g.sizeOfExpression));
    g.unaryExpression.or(or(g.pointerIndirectionExpression, g.addressOfExpression));
    g.pointerIndirectionExpression.is(STAR, g.unaryExpression);
    g.pointerMemberAccess.is(g.primaryExpression, PTR_OP, IDENTIFIER);
    g.pointerElementAccess.is(g.primaryNoArrayCreationExpression, LBRACKET, g.expression, RBRACKET);
    g.addressOfExpression.is(AND, g.unaryExpression);
    g.sizeOfExpression.is(SIZEOF, LPARENTHESIS, g.type, RPARENTHESIS);
    g.fixedStatement.is(FIXED, LPARENTHESIS, g.pointerType, g.fixedPointerDeclarator,
        o2n(COMMA, g.fixedPointerDeclarator), RPARENTHESIS, g.embeddedStatement);
    g.fixedPointerDeclarator.is(IDENTIFIER, EQUAL, g.fixedPointerInitializer);
    // NOTE : g.stackallocInitializer should not be here according to the specifications, but it seems it can in reality
    g.fixedPointerInitializer.isOr(and(AND, g.variableReference), g.stackallocInitializer, g.expression);
    g.structMemberDeclaration.or(g.fixedSizeBufferDeclaration);
    g.fixedSizeBufferDeclaration.is(opt(g.attributes), o2n(g.fixedSizeBufferModifier), FIXED, g.type,
        one2n(g.fixedSizeBufferDeclarator), SEMICOLON);
    g.fixedSizeBufferModifier.isOr(NEW, PUBLIC, PROTECTED, INTERNAL, PRIVATE, UNSAFE);
    g.fixedSizeBufferDeclarator.is(IDENTIFIER, LBRACKET, g.constantExpression, RBRACKET);
    g.localVariableInitializer.or(g.stackallocInitializer);
    g.stackallocInitializer.is(STACKALLOC, g.type, LBRACKET, g.expression, RBRACKET);
  }

}
