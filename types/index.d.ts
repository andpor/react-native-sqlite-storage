// Type definitions for react-native-sqlite-storage 5.0
// Project: https://github.com/andpor/react-native-sqlite-storage
// Definitions by: Sergei Dryganets <https://github.com/dryganets>
//                 Deividi Cavarzan <https://github.com/cavarzan>
// Definitions: https://github.com/DefinitelyTyped/DefinitelyTyped
// TypeScript Version: 2.4
declare module "@mendix/react-native-sqlite-storage" {
    export function DEBUG(isDebug: boolean): void;

    export function openDatabase(params: DatabaseParams): Promise<SQLiteDatabase>;

    export function deleteDatabase(params: DatabaseParams): Promise<void>;

    export type Location = "default" | "Library" | "Documents" | "Shared";

    export interface DatabaseOptionalParams {
        createFromLocation?: number | string | undefined;
        // Database encryption pass phrase
        key?: string | undefined;
        readOnly?: boolean | undefined;
    }

    export interface DatabaseParams extends DatabaseOptionalParams {
        name: string;
        /**
         * Affects iOS database file location
         * 'default': Library/LocalDatabase subdirectory - NOT visible to iTunes and NOT backed up by iCloud
         * 'Library': Library subdirectory - backed up by iCloud, NOT visible to iTunes
         * 'Documents': Documents subdirectory - visible to iTunes and backed up by iCloud
         */
        location: Location;
    }

    export interface ResultSet {
        insertId: number;
        rowsAffected: number;
        rows: ResultSetRowList;
    }

    export interface ResultSetRowList {
        length: number;
        raw: () => any[];
        item: (index: number) => any;
    }

    export interface Transaction {
        executeSql: ((sqlStatement: string, arguments?: any[]) => Promise<[Transaction, ResultSet]>);
    }

    export interface SQLiteDatabase {
        transaction: ((scope: (tx: Transaction) => void) => Promise<Transaction>);
        readTransaction: ((scope: (tx: Transaction) => void) => Promise<Transaction>);
        close: (() => Promise<void>);
        executeSql: ((statement: string, params?: any[]) => Promise<[ResultSet]>);

        attach: ((nameToAttach: string, alias: string) => Promise<void>);

        dettach: ((alias: string) => Promise<void>);
    }
}
