#include "pch.h"

#include "SQLitePlugin.h"

using namespace winrt;
using namespace winrt::Microsoft::ReactNative;
using namespace winrt::Windows::Foundation;
using namespace winrt::Windows::Storage;
using namespace winrt::Windows::Storage::Streams;
using namespace winrt::Windows::Security::Cryptography;

namespace SQLitePlugin
{
    void SQLitePlugin::Attach(
        DatabaseAttachOptions options,
        std::function<void(std::string)> onSuccess,
        std::function<void(std::string)> onFailure) noexcept
    {
        serialReactDispatcher.Post(
            [
                options{ std::move(options) },
                onSuccess{ std::move(onSuccess) },
                onFailure{ std::move(onFailure) },
                weak_this = std::weak_ptr(shared_from_this())
            ]()
        {
            if (auto strongThis = weak_this.lock()) {

                if (options.MainDB == nullptr)
                {
                    onFailure("You must specify a database to attach to");
                    return;
                }

                if (options.DBAlias == nullptr)
                {
                    onFailure("You must specify a database alias to use");
                    return;
                }

                if (options.DBFileToAttach == nullptr)
                {
                    onFailure("You must specify database to attach");
                    return;
                }

                hstring absoluteDbPath;
                absoluteDbPath = ResolveDbFilePath(to_hstring(options.MainDB));

                if (strongThis->openDBs.find(absoluteDbPath) == strongThis->openDBs.end())
                {
                    onFailure("No such database, you must open it first");
                    return;
                }

                hstring absoluteDbPathToAttach;
                absoluteDbPathToAttach = ResolveDbFilePath(to_hstring(options.DBFileToAttach));

                std::string statement = ("ATTACH DATABASE '" + to_string(absoluteDbPathToAttach) + "' AS " + options.DBAlias);

                sqlite3* dbHandle = strongThis->openDBs[absoluteDbPath];

                if (sqlite3_exec(dbHandle, statement.c_str(), NULL, NULL, NULL) == SQLITE_OK)
                {
                    onSuccess("Database successfully attached");
                    return;
                }
                else
                {
                    const char* strPtr = sqlite3_errmsg(dbHandle);
                    std::string errorMessage(strPtr, strlen(strPtr));
                    onFailure("Failed to attach database: " + errorMessage);
                    return;
                }
            }
        });
    }

    void SQLitePlugin::Close(
        DatabaseCloseOptions options,
        std::function<void(std::string)> onSuccess,
        std::function<void(std::string)> onFailure) noexcept
    {
        serialReactDispatcher.Post(
            [
                options{ std::move(options) },
                onSuccess{ std::move(onSuccess) },
                onFailure{ std::move(onFailure) },
                weak_this = std::weak_ptr(shared_from_this())
            ]()
        {

            if (auto strongThis = weak_this.lock()) {

                if (options.Path == nullptr || options.Path.empty())
                {
                    onFailure("You must specify database path");
                    return;
                }

                hstring absoluteDbPath{ ResolveDbFilePath(to_hstring(options.Path)) };

                if (CloseDatabaseIfOpen(absoluteDbPath, strongThis->openDBs))
                {
                    onSuccess("DB Closed");
                }
                else
                {
                    onFailure("Specified db was not open");
                }
            }
        });
    }

    SQLitePlugin::SQLitePlugin() {};

    SQLitePlugin::~SQLitePlugin() {
        for (auto& db : openDBs)
        {
            sqlite3_close(db.second);
        }
    };

    void SQLitePlugin::DeleteDB(
        DatabaseDeleteOptions options,
        std::function<void(std::string)> onSuccess,
        std::function<void(std::string)> onFailure) noexcept
    {
        serialReactDispatcher.Post(
            [
                options{ std::move(options) },
                onSuccess{ std::move(onSuccess) },
                onFailure{ std::move(onFailure) },
                weak_this = std::weak_ptr(shared_from_this())
            ]()
        {
            if (auto strongThis = weak_this.lock())
            {
                if (options.Path == nullptr || options.Path.empty())
                {
                    onFailure("You must specify database name");
                    return;
                }

                hstring absoluteDbPath{ ResolveDbFilePath(to_hstring(options.Path)) };

                CloseDatabaseIfOpen(absoluteDbPath, strongThis->openDBs);

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

    void SQLitePlugin::EchoStringValue(
        EchoStringValueOptions options,
        std::function<void(std::string)> onSuccess,
        std::function<void(std::string)> onFailure) noexcept
    {
        serialReactDispatcher.Post(
            [
                options{ std::move(options) },
                onSuccess{ std::move(onSuccess) }
            ]()
        {
            onSuccess(options.Value);
        });
    }

    void SQLitePlugin::ExecuteSqlBatch(
        ExecuteSqlBatchOptions options,
        std::function<void(std::vector<JSValueObject>)> onSuccess,
        std::function<void(std::string)> onFailure) noexcept
    {
        serialReactDispatcher.Post(
            [
                options{ std::move(options) },
                onSuccess{ std::move(onSuccess) },
                onFailure{ std::move(onFailure) },
                weak_this = std::weak_ptr(shared_from_this())
            ]()
        {
            if (auto strongThis = weak_this.lock()) {
                if (options.DBArgs.DBName == nullptr)
                {
                    onFailure("You must specify database path");
                    return;
                }

                hstring absoluteDbPath;
                absoluteDbPath = ResolveDbFilePath(to_hstring(options.DBArgs.DBName));

                if (strongThis->openDBs.find(absoluteDbPath) == strongThis->openDBs.end())
                {
                    onFailure("No such database, you must open it first");
                    return;
                }

                sqlite3* dbHandle = strongThis->openDBs[absoluteDbPath];
                std::vector<JSValueObject> results;

                for (auto& query : options.Executes)
                {
                    JSValue result;
                    if (ExecuteQuery(dbHandle, query, result))
                    {
                        // Query succeeded
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
                        // Query failed
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

    void SQLitePlugin::Open(
        DatabaseOpenOptions options,
        std::function<void(std::string)> onSuccess,
        std::function<void(std::string)> onFailure) noexcept
    {
        serialReactDispatcher.Post(
            [
                options{ std::move(options) },
                onSuccess{ std::move(onSuccess) },
                onFailure{ std::move(onFailure) },
                weak_this = std::weak_ptr(shared_from_this())
            ]()
        {
            if (auto strongThis = weak_this.lock()) {
                const std::string* dbFileName = &options.Name;

                if (dbFileName == nullptr || dbFileName->empty())
                {
                    onFailure("You must specify the database name");
                    return;
                }

                hstring absoluteDbPath{ ResolveDbFilePath(to_hstring(*dbFileName)) };

                if (strongThis->openDBs.find(absoluteDbPath) != strongThis->openDBs.end())
                {
                    onSuccess("Database opened");
                    return;
                }

                IAsyncOperation<StorageFile> assetFileOp = ResolveAssetFile(options.AssetFileName, *dbFileName);
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

                int openFlags{ 0 };
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
                            CopyDbAsync(assetFile, to_hstring(*dbFileName)).GetResults();
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
                    strongThis->openDBs[absoluteDbPath] = dbHandle;
                    onSuccess("Database opened");
                }
                else
                {
                    onFailure("Unable to open DB");
                }
            }
        });

    }


    void SQLitePlugin::BindStatement(sqlite3_stmt* stmt_ptr, int argIndex, const JSValue& arg)
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
            sqlite3_bind_text(stmt_ptr, argIndex, arg.AsString().c_str(), -1, SQLITE_TRANSIENT);
            break;
        default:
            sqlite3_bind_text(stmt_ptr, argIndex, arg.AsString().c_str(), -1, SQLITE_TRANSIENT);
            break;
        }
    }

    bool SQLitePlugin::CloseDatabaseIfOpen(const hstring& absoluteDbPath, std::map<hstring, sqlite3*>& openDBs)
    {
        if (openDBs.find(absoluteDbPath) != openDBs.end())
        {
            sqlite3* dbHandle = openDBs[absoluteDbPath];

            if (sqlite3_close(dbHandle) != SQLITE_OK)
            {
                std::string debugMessage = "SQLitePluginModule: Error closing database: " + to_string(absoluteDbPath) + "\n";
                OutputDebugStringA(debugMessage.c_str());
            }

            openDBs.erase(absoluteDbPath);
            return true;
        }
        return false;
    }

    IAsyncOperation<StorageFile> SQLitePlugin::CopyDbAsync(const StorageFile& srcDbFile, const hstring& destDbFileName)
    {
        // This implementation is closely related to ResolveDbFilePath.
        return srcDbFile.CopyAsync(ApplicationData::Current().LocalFolder(), destDbFileName, NameCollisionOption::FailIfExists);
    }

    bool SQLitePlugin::ExecuteQuery(sqlite3* dbHandle, const DBQuery& query, JSValue& result)
    {
        if (query.SQL == nullptr || query.SQL == "")
        {
            result = JSValue{ "You must specify a sql query to execute" };
            return false;
        }

        int previousRowsAffected = sqlite3_total_changes(dbHandle);
        sqlite3_stmt* stmt;
        int prepResult = sqlite3_prepare_v2(dbHandle, query.SQL.c_str(), -1, &stmt, nullptr);

        if (prepResult != SQLITE_OK)
        {
            result = JSValue{ sqlite3_errmsg(dbHandle) };
            return false;
        }

        if (!query.Params.empty())
        {
            // sqlite bind uses 1-based indexing for the arguments
            int argIndex = 1;
            for (auto& arg : query.Params)
            {
                BindStatement(stmt, argIndex, arg);
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
            switch (sqlite3_step(stmt))
            {
            case SQLITE_ROW:
                resultRows.push_back(ExtractRow(stmt));
                break;

            case SQLITE_DONE:
            {
                int currentRowsAffected = sqlite3_total_changes(dbHandle);
                rowsAffected = currentRowsAffected - previousRowsAffected;
                sqlite3_int64  currentInsertId = sqlite3_last_insert_rowid(dbHandle);
                if (rowsAffected > 0 && currentInsertId != 0)
                {
                    insertId = currentInsertId;
                }
                keepGoing = false;
                break;
            }
            default:
            {
                const char* strPtr = sqlite3_errmsg(dbHandle);
                std::string errorMessage(strPtr, strlen(strPtr));
                result = JSValue{ errorMessage };
                keepGoing = false;
                isError = true;
            }
            break;
            }
        }

        sqlite3_finalize(stmt);

        if (isError)
        {
            return false;
        }

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

    JSValue SQLitePlugin::ExtractColumn(sqlite3_stmt* stmt, int columnIndex)
    {
        switch (sqlite3_column_type(stmt, columnIndex))
        {
        case SQLITE_INTEGER:
            return sqlite3_column_int64(stmt, columnIndex);
        case SQLITE_FLOAT:
            return sqlite3_column_double(stmt, columnIndex);
        case SQLITE_TEXT:
        {
            const char* strPtr = (char*)sqlite3_column_text(stmt, columnIndex);
            return std::string(strPtr, strlen(strPtr));
        }
        case SQLITE_BLOB:
        {
            // JSON does not support raw binary data. You can't write a binary blob using this module
            // In case we have a pre-populated database with a binary blob in it, 
            // we are going to base64 encode it and return as a string.
            // This is consistent with iOS implementation.
            const void* ptr = sqlite3_column_blob(stmt, columnIndex);
            int length = sqlite3_column_bytes(stmt, columnIndex);
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

    JSValueObject SQLitePlugin::ExtractRow(sqlite3_stmt* stmt)
    {
        JSValueObject row{};
        int columnCount = sqlite3_column_count(stmt);
        for (int i = 0; i < columnCount; i++)
        {
            const char* strPtr = (char*)sqlite3_column_name(stmt, i);
            std::string columnName(strPtr, strlen(strPtr));
            JSValue columnValue = ExtractColumn(stmt, i);
            if (!columnValue.IsNull())
            {
                row[columnName] = std::move(columnValue);
            }
        }
        return row;
    }

    IAsyncOperation<StorageFile> SQLitePlugin::ResolveAssetFile(const std::string& assetFilePath, const std::string& dbFileName)
    {
        if (assetFilePath == nullptr || assetFilePath.length() == 0)
        {
            return nullptr;
        }
        else if (assetFilePath == "1")
        {
            // Build a path to the pre-populated DB asset from app bundle www subdirectory
            return StorageFile::GetFileFromApplicationUriAsync(Uri(
                L"ms-appx:///www/" + winrt::to_hstring(dbFileName)));
        }
        else if (assetFilePath[0] == '~')
        {
            // Build a path to the pre-populated DB asset from app bundle subdirectory
            return StorageFile::GetFileFromApplicationUriAsync(Uri(
                L"ms-appx:///" + winrt::to_hstring(assetFilePath.substr(1))));
        }
        else
        {
            // Build a path to the pre-populated DB asset from app sandbox directory
            return StorageFile::GetFileFromApplicationUriAsync(Uri(
                L"ms-appdata:///local/" + winrt::to_hstring(assetFilePath)));
        }
    }

    hstring SQLitePlugin::ResolveDbFilePath(const hstring dbFileName)
    {
        return ApplicationData::Current().LocalFolder().Path() + L"\\" + dbFileName;
    }
}
