#pragma once

#include "pch.h"

using namespace winrt;
using namespace winrt::Microsoft::ReactNative;
using namespace winrt::Windows::Storage;

namespace SQLitePlugin
{
    REACT_STRUCT(CloseOptions);
    struct CloseOptions
    {
        REACT_FIELD(Path, L"path");
        // Path at which the database is located
        std::string Path;
    };

    REACT_STRUCT(DeleteOptions);
    struct DeleteOptions
    {
        REACT_FIELD(Path, L"path");
        // Path at which the database is located
        std::string Path;
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
        int QID;

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

    struct OpenDB
    {
        sqlite3* Handle;
        hstring Path;
        OpenDB() {
        };
        OpenDB(sqlite3* handle, hstring path)
        {
            Handle = handle;
            Path = path;
        }
    };

    REACT_STRUCT(OpenOptions);
    struct OpenOptions
    {
        REACT_FIELD(Name, L"name");
        // Path at which to store the database
        std::string Name;

        REACT_FIELD(AssetFileName, L"assetFilename");
        // Optional. When creating the DB, uses this file as the initial state.
        std::string AssetFileName;

        REACT_FIELD(ReadOnly, L"readOnly");
        bool ReadOnly;
    };


    REACT_MODULE(SQLitePlugin, L"SQLite");
    struct SQLitePlugin : std::enable_shared_from_this<SQLitePlugin>
    {
    public:
        REACT_METHOD(Attach, L"attach");
        void Attach(
            JSValue options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept;

        REACT_METHOD(Close, L"close");
        void Close(
            CloseOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept;

        REACT_METHOD(DeleteDB, L"delete");
        void DeleteDB(
            DeleteOptions options,
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
            OpenOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept;

        SQLitePlugin::SQLitePlugin();
        SQLitePlugin::~SQLitePlugin();

    private:
        std::map<hstring, OpenDB> OpenDBs;
        ReactDispatcher SerialReactDispatcher{ ReactDispatcher::CreateSerialDispatcher() };

        static IAsyncOperation<StorageFile> CopyDbAsync(const StorageFile& srcDbFile, const hstring& destDbFileName);
        static void BindStatement(sqlite3_stmt* stmt_ptr, int argIndex, const JSValue& arg);
        static bool ExecuteQuery(const OpenDB& db, const DBQuery& query, JSValue& result);
        static JSValue ExtractColumn(sqlite3_stmt* stmtPtr, int columnIndex);
        static JSValueObject ExtractRow(sqlite3_stmt* stmtPtr);
        static IAsyncOperation<StorageFile> ResolveAssetFile(const std::string& assetFilePath, const std::string& dbFileName);
        static hstring ResolveDbFilePath(const hstring dbFileName);
    };
}
