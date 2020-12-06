#include <mutex>
#include <queue>

template<class T>
class ThreadSafeQueue {

private:

    std::queue<T> q;
    std::mutex m;
    bool running = false;

public:

    ThreadSafeQueue() 
    {
    }

    void push(T elem)
    {
        std::lock_guard<std::mutex> lock(m);
        q.push(elem);
    }

    bool next(T& elem)
    {
        std::lock_guard<std::mutex> lock(m);
        if (q.empty()) 
        {
            return false;
        }
        elem = q.front();
        q.pop();
        return true;
    }

    size_t size()
    {
        return q.size();
    }
};