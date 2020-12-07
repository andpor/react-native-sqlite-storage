#pragma once

#include "pch.h"
#include <functional>
#include "NativeModules.h"
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Storage.h>
#include <winrt/Microsoft.ReactNative.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winsqlite/winsqlite3.h>
#include <ppltasks.h>

#include "TaskQueue.h"

using namespace winrt;
using namespace winrt::Windows::Foundation;
using namespace winrt::Windows::Storage;
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
        REACT_FIELD(Name, L"name")
        // Path at which to store the database
        std::string Name;

        REACT_FIELD(AssetFileName, L"assetFilename")
        // Optional. When creating the DB, uses this file as the initial state.
        std::string AssetFileName;

        REACT_FIELD(ReadOnly, L"readOnly")
        bool ReadOnly;
    };

    REACT_STRUCT(CloseOptions);
    struct CloseOptions
    {
        REACT_FIELD(Path, L"path")
            // Path at which the database is located
            std::string Path;
    };

    REACT_STRUCT(DeleteOptions);
    struct DeleteOptions
    {
        REACT_FIELD(Path, L"path")
            // Path at which the database is located
            std::string Path;
    };

    REACT_STRUCT(EchoStringValueOptions);
    struct EchoStringValueOptions
    {
        REACT_FIELD(Value, L"value")
        std::string Value;
    };


    REACT_MODULE(SQLitePlugin, L"SQLite");
    struct SQLitePlugin
    {
        std::map<hstring, OpenDB> OpenDBs;
        TaskQueue SyncTaskQueue;

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

        REACT_METHOD(open, L"open");
        winrt::fire_and_forget open(
            OpenOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            // Schedule the task on the UI thread
            SyncTaskQueue.Queue(concurrency::create_task(
                [options, onSuccess, onFailure, this]()
            {
                std::string dbFileName = options.Name;

                if (dbFileName == nullptr)
                {
                    onFailure("You must specify database name");
                    return;
                }

                hstring absoluteDbPath;
                absoluteDbPath = ResolveDbFilePath(to_hstring(dbFileName));

                if (OpenDBs.find(absoluteDbPath) != OpenDBs.end())
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
                    OpenDBs[absoluteDbPath] = OpenDB(dbHandle, absoluteDbPath);
                    onSuccess("Database opened");
                }
                else
                {
                    onFailure("Unable to open DB");
                }
            }));

            // Return the UI thread and execute work in the background if neede
            co_await resume_background();

            SyncTaskQueue.Run();

        }

        REACT_METHOD(close, L"close");
        winrt::fire_and_forget close(
            CloseOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            // Schedule the task on the UI thread
            SyncTaskQueue.Queue(concurrency::create_task(
                [options, onSuccess, onFailure, this]()
            {
                std::string dbFileName = options.Path;

                if (dbFileName == nullptr)
                {
                    onFailure("You must specify database path");
                    return;
                }

                hstring absoluteDbPath;
                absoluteDbPath = ResolveDbFilePath(to_hstring(dbFileName));

                if (OpenDBs.find(absoluteDbPath) == OpenDBs.end())
                {
                    onFailure("Specified db was not open");
                    return;
                }

                OpenDB db = OpenDBs[absoluteDbPath];
                OpenDBs.erase(absoluteDbPath);

                if (sqlite3_close(db.Handle) != SQLITE_OK)
                {
                    // C# implementation returns success if the DB failed to close
                    // Matching the existing implementation
                    std::string debugMessage = "SQLitePluginModule: Error closing database: " + to_string(absoluteDbPath) + "\n";
                    OutputDebugStringA(debugMessage.c_str());
                }

                onSuccess("DB Closed");
            }));

            // Return the UI thread and execute work in the background if needed
            co_await resume_background();

            SyncTaskQueue.Run();
        }


        REACT_METHOD(echoStringValue, L"echoStringValue");
        winrt::fire_and_forget echoStringValue(
            EchoStringValueOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            // Schedule the task on the UI thread
            SyncTaskQueue.Queue(concurrency::create_task(
                [options, onSuccess, onFailure, this]()
            {
                onSuccess(options.Value);
            }));

            // Return the UI thread and execute work in the background if needed
            co_await resume_background();

            SyncTaskQueue.Run();
        }

        REACT_METHOD(attach, L"attach");
        winrt::fire_and_forget attach(
            JSValueObject options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            // Schedule the task on the UI thread
            SyncTaskQueue.Queue(concurrency::create_task(
                [onSuccess, onFailure, this]()
            {
                onFailure("attach isn't supported on this platform");
            }));

            // Return the UI thread and execute work in the background if needed
            co_await resume_background();

            SyncTaskQueue.Run();

        }

        REACT_METHOD(deleteDB, L"delete");
        winrt::fire_and_forget deleteDB(
            DeleteOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {
            // Schedule the task on the UI thread
            SyncTaskQueue.Queue(concurrency::create_task(
                [options, onSuccess, onFailure, this]()
            {
                std::string dbFileName = options.Path;

                if (dbFileName == nullptr)
                {
                    onFailure("You must specify database name");
                    return;
                }

                hstring absoluteDbPath;
                absoluteDbPath = ResolveDbFilePath(to_hstring(dbFileName));

                if (OpenDBs.find(absoluteDbPath) != OpenDBs.end())
                {
                    OpenDB db = OpenDBs[absoluteDbPath];

                    OpenDBs.erase(absoluteDbPath);

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
            }));

            // Return the UI thread and execute work in the background if needed
            co_await resume_background();

            SyncTaskQueue.Run();
        }
    };
}
