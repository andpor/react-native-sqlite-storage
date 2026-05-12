import type {TurboModule} from 'react-native';
import {TurboModuleRegistry} from 'react-native';

type SQLiteOptions = Object;
type SuccessCallback = (value: Object) => void;
type ErrorCallback = (error: Object) => void;

export interface Spec extends TurboModule {
  open(options: SQLiteOptions, success: SuccessCallback, error: ErrorCallback): void;
  close(options: SQLiteOptions, success: SuccessCallback, error: ErrorCallback): void;
  attach(options: SQLiteOptions, success: SuccessCallback, error: ErrorCallback): void;
  deleteDb(options: SQLiteOptions, success: SuccessCallback, error: ErrorCallback): void;
  backgroundExecuteSqlBatch(options: SQLiteOptions, success: SuccessCallback, error: ErrorCallback): void;
  executeSqlBatch(options: SQLiteOptions, success: SuccessCallback, error: ErrorCallback): void;
  echoStringValue(options: SQLiteOptions, success: SuccessCallback, error: ErrorCallback): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('SQLite');
