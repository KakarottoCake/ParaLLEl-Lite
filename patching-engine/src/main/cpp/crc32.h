#ifndef CRC32_H
#define CRC32_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

uint32_t crc32(const uint8_t *data, size_t length);

#ifdef __cplusplus
}
#endif

#endif // CRC32_H
