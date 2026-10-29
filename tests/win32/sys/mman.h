#pragma once
// Host-test-only adapter. Android/Linux retain the platform mmap implementation.
#ifndef _WIN32
#error This adapter is only for Windows host tests.
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#undef near
#undef far
#undef small
#include <io.h>
#include <cstddef>
#define PROT_READ 1
#define MAP_PRIVATE 2
#define MAP_FAILED ((void*)-1)
inline void* mmap(void*, size_t length, int protection, int flags, int fd, long long offset) {
    if (protection != PROT_READ || flags != MAP_PRIVATE || offset != 0) return MAP_FAILED;
    HANDLE mapping=CreateFileMappingW((HANDLE)_get_osfhandle(fd),nullptr,PAGE_READONLY,0,0,nullptr);
    if (!mapping) return MAP_FAILED;
    void* result=MapViewOfFile(mapping,FILE_MAP_READ,0,0,length);
    CloseHandle(mapping);
    return result ? result : MAP_FAILED;
}
inline int munmap(void* data, size_t) {return UnmapViewOfFile(data) ? 0 : -1;}
