/*
 * Sonar C# Plugin :: C# Squid :: Checks
 * Copyright (C) 2010 Jose Chillan, Alexandre Victoor and SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.sonar.csharp.checks;

import com.sonar.csharp.squid.parser.CSharpGrammar;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;
import org.sonar.squidbridge.checks.SquidCheck;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;

import java.util.List;

@Rule(
  key = "S1301",
  priority = Priority.MAJOR)
@BelongsToProfile(title = CheckList.SONAR_WAY_PROFILE, priority = Priority.MAJOR)
public class AtLeastThreeCasesInSwitchCheck extends SquidCheck<Grammar> {

  @Override
  public void init() {
    subscribeTo(CSharpGrammar.SWITCH_STATEMENT);
  }

  @Override
  public void visitNode(AstNode node) {
    if (hasLessThanThreeCases(node)) {
      getContext().createLineViolation(this, "Replace this \"switch\" statement with \"if\" statements to increase readability.", node);
    }
  }

  private static boolean hasLessThanThreeCases(AstNode switchStmt) {
    List<AstNode> switchSelectionList = switchStmt.getChildren(CSharpGrammar.SWITCH_SECTION);
    int nbCase = 0;

    for (AstNode switchSelection : switchSelectionList) {
      nbCase += switchSelection.getChildren(CSharpGrammar.SWITCH_LABEL).size();
    }

    return nbCase < 3;
  }

}
