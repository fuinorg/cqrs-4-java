/**
 * Copyright (C) 2013 Future Invent Informationsmanagement GmbH. All rights
 * reserved. <http://www.fuin.org/>
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.fuin.cqrs4j;

import java.io.Serializable;

import org.joda.time.DateTime;

/**
 * Basic information shared by all commands.
 */
public abstract class AbstractCommand implements Serializable {

    private static final long serialVersionUID = 1000L;

    private DateTime created;
    
    /**
     * Protected default constructor for de-serialization.
     */
    protected AbstractCommand() {
	super();
	this.created = new DateTime();
    }

    /**
     * Returns the timestamp when the command was created.
     * 
     * @return Date/Time the object was created.
     */
    public final DateTime getCreated() {
        return created;
    }

    
}
