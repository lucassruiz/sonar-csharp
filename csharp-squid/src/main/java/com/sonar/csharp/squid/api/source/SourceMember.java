/*
 * Sonar C# Plugin :: C# Squid :: Squid
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
package com.sonar.csharp.squid.api.source;

import org.sonar.squidbridge.api.SourceCode;

/**
 * SourceCode class that represents a member in C# (methods, properties, ... )
 */
public class SourceMember extends SourceCode {

  /**
   * Creates a new {@link SourceMember} object.
   *
   * @param key
   *          the key of the member
   */
  public SourceMember(String key) {
    super(key);
  }

  /**
   * Creates a new {@link SourceMember} object.
   *
   * @param parent
   *          the parent of this member
   * @param memberSignature
   *          the signature of the member
   * @param startAtLine
   *          the line where this member begins
   */
  public SourceMember(SourceType parent, String memberSignature, int startAtLine) {
    super(parent.getKey() + "#" + memberSignature, memberSignature);
    setStartAtLine(startAtLine);
  }

}
