#pragma once

#include "pch.h"
#include <functional>
#include "JSValue.h"
#include "NativeModules.h"
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Storage.h>
#include <winrt/Windows.Storage.Streams.h>
#include <winrt/Windows.Security.Cryptography.h>
#include <winrt/Microsoft.ReactNative.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winsqlite/winsqlite3.h>

using namespace winrt;
using namespace winrt::Windows::Foundation;
using namespace winrt::Windows::Storage;
using namespace winrt::Windows::Storage::Streams;
using namespace winrt::Windows::Security::Cryptography;
namespace SQLitePlugin
{
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

    REACT_STRUCT(EchoStringValueOptions);
    struct EchoStringValueOptions
    {
        REACT_FIELD(Value, L"value");
        std::string Value;
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
        std::map<hstring, OpenDB> OpenDBs;

        ReactDispatcher SerialReactDispatcher{ ReactDispatcher::CreateSerialDispatcher() };

        static IAsyncOperation<StorageFile> ResolveAssetFile(const std::string& assetFilePath, std::string& dbFileName)
        {
            if (assetFilePath == nullptr || assetFilePath.length() == 0)
            {
                return nullptr;
            }
            else if (assetFilePath == "1")
            {
                // Built path to pre-populated DB asset from app bundle www subdirectory
                return StorageFile::GetFileFromApplicationUriAsync(Uri(
                    L"ms-appx:///www/" + winrt::to_hstring(dbFileName)));
            }
            else if (assetFilePath[0] == '~')
            {
                // Built path to pre-populated DB asset from app bundle subdirectory
                return StorageFile::GetFileFromApplicationUriAsync(Uri(
                    L"ms-appx:///" + winrt::to_hstring(assetFilePath.substr(1))));
            }
            else
            {
                // Built path to pre-populated DB asset from app sandbox directory
                return StorageFile::GetFileFromApplicationUriAsync(Uri(
                    L"ms-appdata:///local/" + winrt::to_hstring(assetFilePath)));
            }
        }

        static hstring ResolveDbFilePath(hstring dbFileName)
        {
            return ApplicationData::Current().LocalFolder().Path() + L"\\" + dbFileName;
        }

        static IAsyncOperation<StorageFile> CopyDbAsync(StorageFile srcDbFile, hstring destDbFileName)
        {
            // This implementation is closely related to ResolveDbFilePath.
            return srcDbFile.CopyAsync(ApplicationData::Current().LocalFolder(), destDbFileName, NameCollisionOption::FailIfExists);
        }

        REACT_METHOD(Open, L"open");
        void Open(
            OpenOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            SerialReactDispatcher.Post(
                [
                    options{ std::move(options) },
                    onSuccess{ std::move(onSuccess) },
                    onFailure{ std::move(onFailure) },
                    weak_this = std::weak_ptr(shared_from_this())
                ]()
            {
                if (auto strongThis = weak_this.lock()) {
                    std::string dbFileName = options.Name;

                    if (dbFileName == nullptr)
                    {
                        onFailure("You must specify database name");
                        return;
                    }

                    hstring absoluteDbPath;
                    absoluteDbPath = ResolveDbFilePath(to_hstring(dbFileName));

                    if (strongThis->OpenDBs.find(absoluteDbPath) != strongThis->OpenDBs.end())
                    {
                        onSuccess("Database opened");
                        return;
                    }

                    IAsyncOperation<StorageFile> assetFileOp = ResolveAssetFile(options.AssetFileName, dbFileName);
                    StorageFile assetFile = nullptr;
                    if (assetFileOp != nullptr)
                    {
                        try
                        {
                            assetFile = assetFileOp.get();
                        }
                        catch (hresult_error const& ex)
                        {
                            onFailure("Unable to open asset file: " + winrt::to_string(ex.message()));
                            return;
                        }
                    }

                    int openFlags = 0;
                    openFlags |= SQLITE_OPEN_NOMUTEX;


                    if (options.ReadOnly && assetFileOp != nullptr)
                    {
                        openFlags |= SQLITE_OPEN_READONLY;
                        absoluteDbPath = assetFile.Path();
                    }
                    else
                    {
                        openFlags |= SQLITE_OPEN_READWRITE;
                        openFlags |= SQLITE_OPEN_CREATE;

                        if (assetFileOp != nullptr)
                        {
                            try
                            {
                                CopyDbAsync(assetFile, to_hstring(dbFileName)).GetResults();
                            }
                            catch (hresult_error const& ex)
                            {
                                // CopyDbAsync throws when the file already exists.
                                onFailure("Unable to copy asset file: " + winrt::to_string(ex.message()));
                                return;
                            }
                        }
                    }

                    sqlite3* dbHandle = nullptr;

                    int result = sqlite3_open_v2(to_string(absoluteDbPath).c_str(), &dbHandle, openFlags, nullptr);
                    if (result == SQLITE_OK)
                    {
                        strongThis->OpenDBs[absoluteDbPath] = OpenDB(dbHandle, absoluteDbPath);
                        onSuccess("Database opened");
                    }
                    else
                    {
                        onFailure("Unable to open DB");
                    }
                }
            });

        }

        REACT_METHOD(Close, L"close");
        void Close(
            CloseOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            SerialReactDispatcher.Post(
                [
                    options{ std::move(options) },
                    onSuccess{ std::move(onSuccess) },
                    onFailure{ std::move(onFailure) },
                    weak_this = std::weak_ptr(shared_from_this())
                ]()
            {

                if (auto strongThis = weak_this.lock()) {
                    std::string dbFileName = options.Path;

                    if (dbFileName == nullptr)
                    {
                        onFailure("You must specify database path");
                        return;
                    }

                    hstring absoluteDbPath;
                    absoluteDbPath = ResolveDbFilePath(to_hstring(dbFileName));

                    if (strongThis->OpenDBs.find(absoluteDbPath) == strongThis->OpenDBs.end())
                    {
                        onFailure("Specified db was not open");
                        return;
                    }

                    OpenDB db = strongThis->OpenDBs[absoluteDbPath];
                    strongThis->OpenDBs.erase(absoluteDbPath);

                    if (sqlite3_close(db.Handle) != SQLITE_OK)
                    {
                        // C# implementation returns success if the DB failed to close
                        // Matching the existing implementation
                        std::string debugMessage = "SQLitePluginModule: Error closing database: " + to_string(absoluteDbPath) + "\n";
                        OutputDebugStringA(debugMessage.c_str());
                    }

                    onSuccess("DB Closed");
                }
            });

        }
        
        REACT_METHOD(EchoStringValue, L"echoStringValue");
        void EchoStringValue(
            EchoStringValueOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            SerialReactDispatcher.Post(
                [
                    options{ std::move(options) },
                    onSuccess{ std::move(onSuccess) }
                ]()
            {
                onSuccess(options.Value);
            });
        }


        REACT_METHOD(Attach, L"attach");
        void Attach(
            JSValue options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            SerialReactDispatcher.Post(
                [onFailure{ std::move(onFailure) }]()
            {
                onFailure("attach isn't supported on this platform");
            });
        }

        REACT_METHOD(DeleteDB, L"delete");
        void DeleteDB(
            DeleteOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            SerialReactDispatcher.Post(
                [
                    options{ std::move(options) },
                    onSuccess{ std::move(onSuccess) },
                    onFailure{ std::move(onFailure) },
                    weak_this = std::weak_ptr(shared_from_this())
                ]()
            {
                if (auto strongThis = weak_this.lock())
                {
                    std::string dbFileName = options.Path;

                    if (dbFileName == nullptr)
                    {
                        onFailure("You must specify database name");
                        return;
                    }

                    hstring absoluteDbPath;
                    absoluteDbPath = ResolveDbFilePath(to_hstring(dbFileName));

                    if (strongThis->OpenDBs.find(absoluteDbPath) != strongThis->OpenDBs.end())
                    {
                        OpenDB db = strongThis->OpenDBs[absoluteDbPath];

                        strongThis->OpenDBs.erase(absoluteDbPath);

                        if (sqlite3_close(db.Handle) != SQLITE_OK)
                        {
                            std::string debugMessage = "SQLitePluginModule: Error closing database: " + to_string(absoluteDbPath) + "\n";
                            OutputDebugStringA(debugMessage.c_str());
                        }
                    }

                    try
                    {
                        StorageFile dbFile = StorageFile::GetFileFromPathAsync(absoluteDbPath).get();
                        dbFile.DeleteAsync().get();
                    }
                    catch (winrt::hresult_error const& ex)
                    {
                        winrt::hstring message = ex.message();
                        std::string errorMessage = "Error deleting database: " + to_string(message) + " " + to_string(absoluteDbPath) + "\n";
                        onFailure(errorMessage);
                        return;
                    }

                    onSuccess("Database Deleted");
                }
            });

            
        }
        
        static void BindStatement(sqlite3_stmt* stmt_ptr, int argIndex, const JSValue& arg)
        {
            switch (arg.Type())
            {
            case JSValueType::Null:
                sqlite3_bind_null(stmt_ptr, argIndex);
                break;
            case JSValueType::Boolean:
                sqlite3_bind_int(stmt_ptr, argIndex, arg.AsBoolean());
                break;
            case JSValueType::Int64:
                sqlite3_bind_int64(stmt_ptr, argIndex, arg.AsInt64());
                break;
            case JSValueType::Double:
                sqlite3_bind_double(stmt_ptr, argIndex, arg.AsDouble());
                break;
            case JSValueType::String:
                sqlite3_bind_text(stmt_ptr, argIndex, arg.AsString().c_str(), -1, nullptr);
                break;
            default:
                sqlite3_bind_text(stmt_ptr, argIndex, arg.AsString().c_str(), -1, nullptr);
                break;
            }
        }

        static JSValue ExtractColumn(sqlite3_stmt* stmtPtr, int columnIndex)
        {
            switch (sqlite3_column_type(stmtPtr, columnIndex))
            {
            case SQLITE_INTEGER:
                return sqlite3_column_int64(stmtPtr, columnIndex);
            case SQLITE_FLOAT:
                return sqlite3_column_double(stmtPtr, columnIndex);
            case SQLITE_TEXT:
                {
                    const char* strPtr = (char*)sqlite3_column_text(stmtPtr, columnIndex);
                    return std::string(strPtr, strlen(strPtr));
                }
            case SQLITE_BLOB:
                {
                    // JSON does not support raw binary data. You can't write a binary blob using this module
                    // In case we have a pre-populated database with a binary blob in it, 
                    // we are going to base64 encode it and return as a string.
                    // This is consistend with iOS implementation.
                    const void* ptr = sqlite3_column_blob(stmtPtr, columnIndex);
                    int length = sqlite3_column_bytes(stmtPtr, columnIndex);
                    Buffer buffer = Buffer(length);
                    memcpy(buffer.data(), ptr, length);
                    return to_string(CryptographicBuffer::EncodeToBase64String(buffer));
                }
            case SQLITE_NULL:
            default:
                return nullptr;
                break;
            }
        }

        enum WebSQLError
        {
            Unknown = 0,
            Database = 1,
            Version = 2,
            TooLarge = 3,
            Quota = 4,
            Syntax = 5,
            Constraint = 6,
            Timeout = 7
        };

        static JSValueObject ExtractRow(sqlite3_stmt* stmtPtr)
        {
            JSValueObject row{};
            int columnCount = sqlite3_column_count(stmtPtr);
            for (int i = 0; i < columnCount; i++)
            {
                const char* strPtr = (char*)sqlite3_column_name(stmtPtr, i);
                std::string columnName(strPtr, strlen(strPtr));
                JSValue columnValue = ExtractColumn(stmtPtr, i);
                if (!columnValue.IsNull())
                {
                    row[columnName] = std::move(columnValue);
                }
            }
            return row;
        }

        static bool ExecuteQuery(const OpenDB db, const DBQuery& query, JSValue & result)
        {
            if (query.SQL == nullptr || query.SQL == "")
            {
                result = JSValue{ "You must specify a sql query to execute" };
                return false;
            }

            int previousRowsAffected = sqlite3_total_changes(db.Handle);
            sqlite3_stmt* stmtPtr;
            int prepResult = sqlite3_prepare_v2(db.Handle, query.SQL.c_str(), -1, &stmtPtr, nullptr);

            if (prepResult != SQLITE_OK)
            {
                result = JSValue{ sqlite3_errmsg(db.Handle) };
                return false;
            }

            if (!query.Params.empty())
            {
                int argIndex = 0;
                for (auto& arg : query.Params)
                {
                    BindStatement(stmtPtr, argIndex, arg);
                    argIndex++;
                }
            }

            std::vector<JSValue> resultRows{};

            bool keepGoing = true;
            int rowsAffected = 0;
            sqlite3_int64 insertId = 0;
            bool isError = false;

            while (keepGoing)
            {
                switch (sqlite3_step(stmtPtr))
                {
                case SQLITE_ROW:
                    resultRows.push_back(ExtractRow(stmtPtr));
                    break;

                case SQLITE_DONE:
                    {
                        int currentRowsAffected = sqlite3_total_changes(db.Handle);
                        rowsAffected = currentRowsAffected - previousRowsAffected;
                        sqlite3_int64  currentInsertId = sqlite3_last_insert_rowid(db.Handle);
                        if (rowsAffected > 0 && currentInsertId != 0)
                        {
                            insertId = currentInsertId;
                        }
                        keepGoing = false;
                        break;
                    }
                default:
                    {
                        const char* strPtr = (char*)sqlite3_errmsg(db.Handle);
                        std::string errorMessage(strPtr, strlen(strPtr));
                        result = JSValue{ errorMessage };
                        keepGoing = false;
                        isError = true;
                    }
                    break;
                }
            }

            sqlite3_finalize(stmtPtr);

            if (isError)
            {
                return false;
            }
            //JSValueArray tempValue{ std::move(resultRows) };
            JSValueObject resultSet =
            {
                { "rows", std::move(resultRows) },
                { "rowsAffected", rowsAffected }
            };
            if (insertId != 0)
            {
                resultSet["insertId"] = insertId;
            }
            result = JSValue(std::move(resultSet));
            return true;
        }

        REACT_METHOD(ExecuteSqlBatch, L"executeSqlBatch");
        REACT_METHOD(ExecuteSqlBatch, L"backgroundExecuteSqlBatch");
        void ExecuteSqlBatch(
            ExecuteSqlBatchOptions options,
            std::function<void(std::vector<JSValueObject>)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            SerialReactDispatcher.Post(
                [
                    options{std::move(options)}, 
                    onSuccess{std::move(onSuccess)}, 
                    onFailure{std::move(onFailure)}, 
                    weak_this = std::weak_ptr(shared_from_this())
                ]()
            {
                if (auto strongThis = weak_this.lock()) {
                    std::string dbFileName = options.DBArgs.DBName;

                    if (dbFileName == nullptr)
                    {
                        onFailure("You must specify database path");
                        return;
                    }

                    hstring absoluteDbPath;
                    absoluteDbPath = ResolveDbFilePath(to_hstring(dbFileName));

                    if (strongThis->OpenDBs.find(absoluteDbPath) == strongThis->OpenDBs.end())
                    {
                        onFailure("No such database, you must open it first");
                        return;
                    }

                    OpenDB db = strongThis->OpenDBs[absoluteDbPath];
                    std::vector<JSValueObject> results;

                    for (auto &query : options.Executes)
                    {
                        JSValue result;
                        if (ExecuteQuery(db, query, result))
                        {
                            //success
                            results.push_back(
                                {
                                    { "qid", query.QID },
                                    { "type", "success" },
                                    { "result", std::move(result)}
                                }
                            );
                        }
                        else
                        {
                            //query failed
                            results.push_back(
                                {
                                    { "qid", query.QID },
                                    { "type", "error" },
                                    { "error", result.AsString()},
                                    { "result", result.AsString()}
                                }
                            );
                        }
                    }
                    onSuccess(std::move(results));
                }
            });
            
            
        }
        
    };
}
