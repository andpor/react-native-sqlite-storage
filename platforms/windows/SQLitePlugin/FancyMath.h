#pragma once

#include "pch.h"
#include <functional>
#include "NativeModules.h"
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Storage.h>
#include <winrt/Microsoft.ReactNative.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winsqlite/winsqlite3.h>

using namespace winrt::Windows::Foundation;
using namespace winrt::Windows::Storage;
namespace SQLitePlugin
{
    class OpenDB
    {
        sqlite3* Handle;
        std::string Path;

        OpenDB(sqlite3* handle, std::string path)
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


    REACT_MODULE(SQLitePlugin, L"SQLite");
    struct SQLitePlugin
    {
        std::map<std::string, OpenDB> OpenDBs;

        IAsyncOperation<StorageFile> ResolveAssetFile(std::string& assetFilePath, std::string& dbFileName)
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

        REACT_METHOD(open, L"open");
        winrt::fire_and_forget open(
            OpenOptions options,
            std::function<void(std::string)> onSuccess,
            std::function<void(std::string)> onFailure) noexcept
        {

            std::string dbFileName = options.Name;

            if (dbFileName == nullptr)
            {
                onFailure("You must specify database name");
                return;
            }

            if (OpenDBs.find(dbFileName) != OpenDBs.end())
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
                    assetFile = co_await assetFileOp;
                }
                catch (winrt::hresult_error const& ex)
                {
                    onFailure("Unable to open asset file: " + winrt::to_string(ex.message()));
                    co_return;
                }
            }
            


            onSuccess("TwoCallbacksMethod succeeded");

        }
    };
}
