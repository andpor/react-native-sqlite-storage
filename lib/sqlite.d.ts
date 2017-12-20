declare module "react-native-sqlite-storage" {
    export interface Error { // this interface should match es5 error.
        name: string;
        message: string;
        stack?: string;
    }

    export type SuccessCallback = () => void;
    export type SuccessDatabaseCallback = (db: Database) => void;
    export type SuccessStatementCallback = (result: Result) => void;
    export type ErrorCallback = (er: Error) => void;

    export interface Result {
        rowAffected: number;
        insertId?: number;
        rows: {
            lenght: number;
            item(index: number): any;
        };
    }

    export interface Transaction {
        executeSql(statement: string, params?: any[], onSuccess?: (tx: Transaction, result: Result) => void, onError?: (tx: Transaction, error: Error) => void): void;
    }

    export interface Database {
        executeSql(statement: string, params?: any[], onSuccess?: SuccessCallback, onError?: ErrorCallback): void;
        sqlBatch(statements: Array<string|[string, any[]]>, onSuccess?: SuccessCallback, onError?: ErrorCallback): void;
        transaction(fn: (tx: Transaction) => void, onError?: ErrorCallback, onSuccess?: SuccessCallback): void;
        readTransaction(fn: (tx: Transaction) => void, onError?: ErrorCallback, onSuccess?: SuccessCallback): void;
        close(onSuccess: SuccessCallback, onError?: ErrorCallback): void;

        executeSql(statement: string, params?: any[]): Promise<any>;
        sqlBatch(statements: Array<string|[string, any[]]>): Promise<any>;
        transaction(fn: (tx: Transaction) => void): Promise<any>;
        readTransaction(fn: (tx: Transaction) => void): Promise<any>;
        close(): Promise<any>; // gives you a status.

        abortAllPendingTransactions(): void;
        open(onSuccess?: SuccessCallback, onError?: ErrorCallback): void;
        close(onSuccess?: SuccessCallback, onError?: ErrorCallback): void;
        attach(name: string, alias: string, onSuccess?: SuccessCallback, onError?: ErrorCallback): void;
        detach(alias: string, onSuccess?: SuccessCallback, onError?: ErrorCallback): void;
    }

    export interface OpenParams {
        [key: string]: any;
        name: string;
        location?: string;
        createFromLocation?: string;
    }

    export interface DeleteParams {
        name: string;
        location?: string | any;
    }

    export interface SqlitePlugin {
        databaseFeatures: any;
        openDBs: any;

        DEBUG(enable: boolean): void;
        enablePromise(enable: boolean): void;
        sqliteFeatures(): any;
        openDatabase(name: string, version: string, displayName: string, size: number, onOpen: SuccessCallback, onError: ErrorCallback): Database;
        openDatabase(params: OpenParams): Promise<Database>;
        deleteDatabase(args: string | DeleteParams, onSuccess: SuccessCallback, onError: ErrorCallback): void;
        deleteDatabase(args: string | DeleteParams): Promise<void>;
        
        selfTest(onSuccess?: SuccessCallback, onError?: ErrorCallback): void;
        echoTest(isItOk?: (value: string) => void, onError?: (msg: string) => void): void;
    }

    export let sqlitePlugin: SqlitePlugin;
    export default sqlitePlugin;
}