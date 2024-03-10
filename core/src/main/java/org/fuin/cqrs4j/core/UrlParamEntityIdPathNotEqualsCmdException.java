package org.fuin.cqrs4j.core;

import org.fuin.ddd4j.core.EntityIdPath;

/**
 * The entity identifier path constructed from the URL does not match the one that is inside the received command.
 * <p>
 * This can happen if URL for example contains the name of the aggregate, followed by an aggregate identifier.<br>
 * Example: POST /customer/f832a5a4-dd80-49df-856a-7274de82cd6b/create (Command send in the request body)<br>
 * The ID from the URL must match the aggregate ID that is passed via the command in the body.
 */
@SuppressWarnings("rawtypes")
public class UrlParamEntityIdPathNotEqualsCmdException extends Exception {

    private final EntityIdPath urlEntityIdPath;

    private final AggregateCommand command;

    /**
     * Constructor with mandatory data.
     *
     * @param urlEntityIdPath Entity identifier path constructed from the URL.
     * @param command         Command with the entity identifier path that does not match the one from the URL.
     */
    public UrlParamEntityIdPathNotEqualsCmdException(EntityIdPath urlEntityIdPath, AggregateCommand command) {
        super("Entity path constructed from URL parameters '" + urlEntityIdPath.asBaseType() + "' " +
                "is not the same as command's entityPath: '" + command.getEntityIdPath().asBaseType() + "'");
        this.urlEntityIdPath = urlEntityIdPath;
        this.command = command;
    }

    /**
     * Returns the entity identifier path constructed from the URL.
     *
     * @return Entity ID path from URL.
     */
    public EntityIdPath getUrlEntityIdPath() {
        return urlEntityIdPath;
    }

    /**
     * Returns the command with the entity identifier path that does not match the one from the URL.
     *
     * @return Command with mismatching entity identifier path.
     */
    public AggregateCommand getCommand() {
        return command;
    }
}
