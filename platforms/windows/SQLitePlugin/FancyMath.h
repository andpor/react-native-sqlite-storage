#pragma once

#include "pch.h"
#include <functional>
#include "NativeModules.h"
#include <winsqlite/winsqlite3.h>

namespace SQLitePlugin
{
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
        REACT_METHOD(open, L"open");
        void open(
            OpenOptions options,
            std::function<void(std::string)>&& onSuccess,
            std::function<void(std::string)>&& onFailure) noexcept {

            std::string dbFileName = options.Name;

            if (dbFileName == nullptr) {
                onFailure("You must specify database name");
            }

            onSuccess("TwoCallbacksMethod succeeded");

        }
    };
}
