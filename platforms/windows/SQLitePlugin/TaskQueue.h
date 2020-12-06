#pragma once
#include <ppltasks.h>
#include "ThreadSafeQueue.h"

using namespace winrt::Windows::Foundation;
class TaskQueue {

private:

    ThreadSafeQueue<concurrency::task<void>> SynchronousTaskQueue;
    bool Running = false;

    void StartWorkLoopIfNeeded()
    {
        if (Running)
        {
            return;
        }
        Running = true;
        while (SynchronousTaskQueue.size() > 0)
        {
            concurrency::task<void> taskToRun;
            if (SynchronousTaskQueue.next(taskToRun) == false)
            {
                continue;
            }
            taskToRun.get();
        }
        Running = false;
    }

public:

    TaskQueue()
    {
    }


    void RunOrQueue(concurrency::task<void> task)
    {
        SynchronousTaskQueue.push(task);
        StartWorkLoopIfNeeded();
    }

    void Queue(concurrency::task<void> task)
    {
        SynchronousTaskQueue.push(task);
    }

    void Run()
    {
        StartWorkLoopIfNeeded();
    }

};