#pragma once

#include "pch.h"

#include <functional>
#define _USE_MATH_DEFINES
#include <math.h>

#include "NativeModules.h"

namespace SQLitePluginModule
{
    REACT_MODULE(SQLitePlugin);
    struct SQLitePlugin
    {
        REACT_CONSTANT(E);
        const double E = M_E;

        REACT_CONSTANT(PI, L"Pi");
        const double PI = M_PI;

        REACT_METHOD(Add, L"add");
        double Add(double a, double b) noexcept
        {
            double result = a + b;
            AddEvent(result);
            return result;
        }

        REACT_EVENT(AddEvent);
        std::function<void(double)> AddEvent;
    };
}
