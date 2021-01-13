#pragma once

#include "pch.h"

using namespace winrt;
using namespace winrt::Microsoft::ReactNative;
using namespace winrt::Windows::Storage;

namespace SQLitePlugin
{
    /*
    * SQLite Plugin for React Native.
    *
    * As of now, it supports callbacks only.
    * All operations on the database have to be serialized to avoid race conditions.
    * Each operation posts a task to SerialReactDispatcher
    * which executes them sequentially in the background.
    */

    REACT_STRUCT(DatabaseAttachOptions);
    struct DatabaseAttachOptions
    {
        REACT_FIELD(MainDB, L"path");
        // path to current database instance
        std::string MainDB;

        REACT_FIELD(DBAlias, L"dbAlias");
        // the Alias that should be use with ATTACH DATABASE
        std::string DBAlias;

        REACT_FIELD(DBFileToAttach, L"dbName");
        // dbName that should be attached to MainDB
        std::string DBFileToAttach;
    };

    REACT_STRUCT(DatabaseCloseOptions);
    struct DatabaseCloseOptions
    {
        REACT_FIELD(Path, L"path");
        // Path at which the database is located
        std::string Path;
    };

    REACT_STRUCT(DatabaseDeleteOptions);
    struct DatabaseDeleteOptions
    {
        REACT_FIELD(Path, L"path");
        // Path at which the database is located
        std::string Path;
    };

    REACT_STRUCT(DatabaseOpenOptions);
    struct DatabaseOpenOptions
    {
        REACT_FIELD(Name, L"name");
        // Path at which to store the database
        std::string Name;

        REACT_FIELD(AssetFileName, L"assetFilename");
        // Optional. When creating the DB, uses this file as the initial state.
        std::string AssetFileName;

        REACT_FIELD(ReadOnly, L"readOnly");
        bool ReadOnly = false;
    };

    REACT_STRUCT(DBArgs);
    struct DBArgs
    {
        REACT_FIELD(DBName, L"dbname");
        std::string DBName;
    };

    REACT_STRUCT(DBQuery);
    struct DBQuery
    {
        REACT_FIELD(QID, L"qid");
        int QID = 0;

        REACT_FIELD(Params, L"params");
        JSValueArray Params; // optional

        REACT_FIELD(SQL, L"sql");
        std::string SQL;
    };

    REACT_STRUCT(EchoStringValueOptions);
    struct EchoStringValueOptions
    {
        REACT_FIELD(Value, L"value");
        std::string Value;
    };

    REACT_STRUCT(ExecuteSqlBatchOptions);
    struct ExecuteSqlBatchOptions
    {
        REACT_FIELD(DBArgs, L"dbargs");
        DBArgs DBArgs;
        REACT_FIELD(Executes, L"executes");
        std::vector<DBQuery> Executes;
    };

    REACT_MODULE(SQLitePlugin, L"SQLite");
    struct SQLitePlugin : std::enable_shared_from_this<SQLitePlugin>
    {
    public:
        REACT_METHOD(Attach, L"attach");
        void Attach(
            DatabaseAttachOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept;

        REACT_METHOD(Close, L"close");
        void Close(
            DatabaseCloseOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept;

        REACT_METHOD(DeleteDB, L"delete");
        void DeleteDB(
            DatabaseDeleteOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept;

        REACT_METHOD(EchoStringValue, L"echoStringValue");
        void EchoStringValue(
            EchoStringValueOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept;

        REACT_METHOD(ExecuteSqlBatch, L"executeSqlBatch");
        REACT_METHOD(ExecuteSqlBatch, L"backgroundExecuteSqlBatch");
        void ExecuteSqlBatch(
            ExecuteSqlBatchOptions options,
            std::function<void(std::vector<JSValueObject>)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept;

        REACT_METHOD(Open, L"open");
        void Open(
            DatabaseOpenOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept;

        SQLitePlugin::SQLitePlugin();
        SQLitePlugin::~SQLitePlugin();

    private:
        std::map<hstring, sqlite3*> openDBs;
        ReactDispatcher serialReactDispatcher{ ReactDispatcher::CreateSerialDispatcher() };

        static void BindStatement(sqlite3_stmt* stmt_ptr, int argIndex, const JSValue& arg);
        static bool CloseDatabaseIfOpen(const hstring& absoluteDbPath, std::map<hstring, sqlite3*>& openDBs);
        static IAsyncOperation<StorageFile> CopyDbAsync(const StorageFile& srcDbFile, const hstring& destDbFileName);
        static bool ExecuteQuery(sqlite3* db, const DBQuery& query, JSValue& result);
        static JSValue ExtractColumn(sqlite3_stmt* stmt, int columnIndex);
        static JSValueObject ExtractRow(sqlite3_stmt* stmt);
        static IAsyncOperation<StorageFile> ResolveAssetFile(const std::string& assetFilePath, const std::string& dbFileName);
        static hstring ResolveDbFilePath(const hstring dbFileName);
    };
}
