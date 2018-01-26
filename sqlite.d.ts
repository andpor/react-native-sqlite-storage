
declare module 'react-native-sqlite-storage' {
    export function openDatabase(params: DatabaseParams, success?: Function, error?: Function): SQLiteDatabase;
    export function deleteDatabase(params: DatabaseParams, success?: Function, error?: Function);
    export type Location = 'default' | 'Library' | 'Documents';
    export interface DatabaseOptionalParams {
        createFromLocation?: number | string;
        // Database encryption pass phrase
        key?: string;
        readOnly?: boolean;
    }

    export interface DatabaseParams extends DatabaseOptionalParams {
        name: string;
        location: number;
    }

    interface ResultSet {
        insertId: number;
        rowsAffected: number;
        rows: ResultSetRowList;
    }

    interface ResultSetRowList {
        length: number;
        item(index: number): any;
    }

    enum SQLErrors {
        UNKNOWN_ERR = 0,
        DATABASE_ERR = 1,
        VERSION_ERR = 2,
        TOO_LARGE_ERR = 3,
        QUOTA_ERR = 4,
        SYNTAX_ERR = 5,
        CONSTRAINT_ERR = 6,
        TIMEOUT_ERR = 7
    }

    interface SQLError {
        code: number;
        message: string;
    }

    interface StatementCallback {
        (transaction: Transaction, resultSet: ResultSet): void;
    }

    interface StatementErrorCallback {
        (transaction: Transaction, error: SQLError): void;
    }

    export interface Transaction {
        executeSql(sqlStatement: string, arguments?: any[], callback?: StatementCallback, errorCallback?: StatementErrorCallback);
    }

    interface TransactionCallback {
        (transaction: Transaction): void;
    }

    interface TransactionErrorCallback {
        (error: SQLError): void;
    }

    export interface SQLiteDatabase {
        transaction(transaction: Transaction, error: TransactionErrorCallback, success: TransactionCallback): void;
        readTransaction(transaction: Transaction, error: TransactionErrorCallback, success: TransactionCallback): void;
        open(success: Function, error: Function): void;
        close(success: Function, error: Function): void;
        executeSql(statement: string, params?: any[], success?: StatementCallback, error?: StatementErrorCallback): void;
        attach(nameToAttach: string, alias: string, success?: Function, error?: Function);
        dettach(alias: string, success?: Function, error?:Function);
    }

}
