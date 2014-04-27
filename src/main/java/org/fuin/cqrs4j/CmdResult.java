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

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.Nullable;

/**
 * Result of a command.
 */
@XmlRootElement(name = "cmd-result")
public final class CmdResult implements Serializable {

	private static final long serialVersionUID = 1000L;

	@NotNull
	@XmlAttribute(name = "type")
	private CmdResultType type;

	@NotNull
	@XmlAttribute(name = "code")
	private Integer code;

	@NotNull
	@XmlAttribute(name = "message")
	private String message;

    @Nullable
    @XmlAnyElement(lax = true)
    private Object data;
	
	/**
	 * Protected default constructor for de-serialization.
	 */
	protected CmdResult() {
		super();
	}

	/**
	 * Constructor with all data.
	 * 
	 * @param type
	 *            Type.
	 * @param code
	 *            Code.
	 * @param message
	 *            Message.
	 */
	public CmdResult(@NotNull final CmdResultType type,
			@NotNull final Integer code, @NotNull final String message) {
		this(type, code, message, null);
	}
	
	/**
	 * Constructor with all data.
	 * 
	 * @param type
	 *            Type.
	 * @param code
	 *            Code.
	 * @param message
	 *            Message.
	 * @param data
	 *            Additional data or NULL.
	 */
	public CmdResult(@NotNull final CmdResultType type,
			@NotNull final Integer code, @NotNull final String message, final Object data) {
		super();
		Contract.requireArgNotNull("type", type);
		Contract.requireArgNotNull("code", code);
		Contract.requireArgNotNull("message", message);
		this.type = type;
		this.code = code;
		this.message = message;
		this.data = data;
	}

	/**
	 * Returns the result type.
	 * 
	 * @return Type.
	 */
	public final CmdResultType getType() {
		return type;
	}

	/**
	 * Returns the result code.
	 * 
	 * @return Code.
	 */
	public final Integer getCode() {
		return code;
	}

	/**
	 * Returns the result message.
	 * 
	 * @return Message.
	 */
	public final String getMessage() {
		return message;
	}

	/**
	 * Returns additional data.
	 * 
	 * @return Result of the executing the command.
	 */
	public final Object getData() {
		return data;
	}
	
	// CHECKSTYLE:OFF Generated code
	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + ((code == null) ? 0 : code.hashCode());
		result = prime * result + ((message == null) ? 0 : message.hashCode());
		return result;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CmdResult other = (CmdResult) obj;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		if (code == null) {
			if (other.code != null)
				return false;
		} else if (!code.equals(other.code))
			return false;
		if (message == null) {
			if (other.message != null)
				return false;
		} else if (!message.equals(other.message))
			return false;
		return true;
	}

	// CHECKSTYLE:OFF Generated code

	@Override
	public final String toString() {
		return type + " " + message + " [" + code + "]";
	}

}
