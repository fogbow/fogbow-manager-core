package org.fogbowcloud.manager.core.datastore.commands;

public class TimestampSQLCommands extends OrderTimestampTableAttributes {

    public static final String CREATE_TIMESTAMP_TABLE_SQL = "CREATE TABLE IF NOT EXISTS " + TIMESTAMP_TABLE_NAME
            + "(" + ORDER_ID + " VARCHAR(255), " + ORDER_STATE + " VARCHAR(255), " + FEDERATION_USER_ID + " VARCHAR(255), "
            + REQUESTING_MEMBER + " VARCHAR(255), " 
            + PROVIDING_MEMBER + " VARCHAR(255), " + TIMESTAMP 
            + " TIMESTAMP, PRIMARY KEY (" + ORDER_ID + ", " + ORDER_STATE + "))";

    public static final String INSERT_TIMESTAMP_SQL = "INSERT INTO " + TIMESTAMP_TABLE_NAME
            + " (" + ORDER_ID + "," + ORDER_STATE + "," + FEDERATION_USER_ID + ","
            + REQUESTING_MEMBER + "," + PROVIDING_MEMBER + "," + TIMESTAMP + ")"
            + " VALUES (?,?,?,?,?,?)";
    
    
    public static final String SELECT_TIMESTAMP_BY_ORDER_ID_SQL = "SELECT * FROM " + TIMESTAMP_TABLE_NAME
            + " WHERE " + ORDER_ID + " = ?";
}
