package org.fogbowcloud.manager.core.datastore.commands;

public class TimestampSQLCommands extends OrderTableAttributes {

    public static final String CREATE_TIMESTAMP_TABLE_SQL = "CREATE TABLE IF NOT EXISTS " + TIMESTAMP_TABLE_NAME
            + "(" + ORDER_ID + " VARCHAR(255), " + ORDER_STATE + " VARCHAR(255), " + FEDERATION_USER_ID + " VARCHAR(255), "
            + TIMESTAMP + " TIMESTAMP, PRIMARY KEY (" + ORDER_ID + ", " + ORDER_STATE + "))";

    public static final String INSERT_TIMESTAMP_SQL = "INSERT INTO " + TIMESTAMP_TABLE_NAME
            + " (" + ORDER_ID + "," + ORDER_STATE + "," + FEDERATION_USER_ID + "," + TIMESTAMP + ")"
            + " VALUES (?,?,?,?)";
}
