////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2017 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle;

/**
 * This interface means that the check will not be cloned during the application execution.
 * Could be used in two cases:
 * 1. When a check is stateless.
 * The stateless check could be executed over several files at once (within different threads).
 * 2. When a check is global stateful.
 * The global stateful may accumulate information during all the application lifetime.
 * In both cases, the check methods should be thread safe.
 * @author Andrew Kuchev
 */
public interface OneCheckInstancePerApplication {
}
