#include "libbps.h"
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include "crc32.h"

static uint32_t read32(const uint8_t * ptr)
{
    uint32_t out;
    out = ptr[0];
    out |= ptr[1] << 8;
    out |= ptr[2] << 16;
    out |= ptr[3] << 24;
    return out;
}

enum { SourceRead, TargetRead, SourceCopy, TargetCopy };

static bool try_add(size_t& a, size_t b)
{
    if (SIZE_MAX - a < b) return false;
    a += b;
    return true;
}

static bool try_shift(size_t& a, size_t b)
{
    if (SIZE_MAX >> b < a) return false;
    a <<= b;
    return true;
}

static bool decodenum(const uint8_t*& ptr, size_t& out)
{
    out = 0;
    unsigned int shift = 0;
    while (true)
    {
        uint8_t next = *ptr++;
        size_t addthis = (next & 0x7F);
        if (shift) addthis++;
        if (!try_shift(addthis, shift)) return false;
        if (!try_add(out, addthis)) return false;
        if (next & 0x80) return true;
        shift += 7;
    }
}

#define error(which) do { error_code = which; goto exit; } while(0)

enum bpserror bps_apply(struct mem patch, struct mem in, struct mem * out, struct mem * metadata, bool accept_wrong_input)
{
    enum bpserror error_code = bps_ok;
    out->len = 0;
    out->ptr = NULL;
    if (metadata)
    {
        metadata->len = 0;
        metadata->ptr = NULL;
    }
    if (patch.len < 4 + 3 + 12) return bps_broken;
    
    {
#define read8() (*(patchat++))
#define decodeto(var) \
                do { \
                    if (!decodenum(patchat, var)) error(bps_too_big); \
                } while(false)
#define write8(byte) (*(outat++) = byte)
        
        const uint8_t * patchat = patch.ptr;
        const uint8_t * patchend = patch.ptr + patch.len - 12;
        
        if (read8() != 'B') error(bps_broken);
        if (read8() != 'P') error(bps_broken);
        if (read8() != 'S') error(bps_broken);
        if (read8() != '1') error(bps_broken);
        
        uint32_t crc_in_e = read32(patch.ptr + patch.len - 12);
        uint32_t crc_out_e = read32(patch.ptr + patch.len - 8);
        uint32_t crc_patch_e = read32(patch.ptr + patch.len - 4);
        
        uint32_t crc_in_a = crc32(in.ptr, in.len);
        uint32_t crc_patch_a = crc32(patch.ptr, patch.len - 4);
        
        if (crc_patch_a != crc_patch_e) error(bps_broken);
        
        size_t inlen;
        decodeto(inlen);
        
        size_t outlen;
        decodeto(outlen);
        
        if (inlen != in.len || crc_in_a != crc_in_e)
        {
            if (in.len == outlen && crc_in_a == crc_out_e) error_code = bps_to_output;
            else error_code = bps_not_this;
            if (!accept_wrong_input) goto exit;
        }
        
        out->len = outlen;
        out->ptr = (uint8_t*)malloc(outlen);
        if (!out->ptr) error(bps_out_of_mem);
        
        const uint8_t * instart = in.ptr;
        const uint8_t * inreadat = in.ptr;
        const uint8_t * inend = in.ptr + in.len;
        
        uint8_t * outstart = out->ptr;
        uint8_t * outreadat = out->ptr;
        uint8_t * outat = out->ptr;
        uint8_t * outend = out->ptr + out->len;
        
        size_t metadatalen;
        decodeto(metadatalen);
        
        if (metadata && metadatalen)
        {
            metadata->len = metadatalen;
            metadata->ptr = (uint8_t*)malloc(metadatalen + 1);
            if (!metadata->ptr) error(bps_out_of_mem);
            for (size_t i = 0; i < metadatalen; i++) metadata->ptr[i] = read8();
            metadata->ptr[metadatalen] = '\0';
        }
        else
        {
            for (size_t i = 0; i < metadatalen; i++) (void)read8();
        }
        
        while (patchat < patchend)
        {
            size_t thisinstr;
            decodeto(thisinstr);
            size_t length = (thisinstr >> 2) + 1;
            int action = (thisinstr & 3);
            if (outat + length > outend) error(bps_broken);
            
            switch (action)
            {
                case SourceRead:
                {
                    if (outat - outstart + length > in.len) error(bps_broken);
                    for (size_t i = 0; i < length; i++)
                    {
                        size_t pos = outat - outstart;
                        write8(instart[pos]);
                    }
                }
                break;
                case TargetRead:
                {
                    if (patchat + length > patchend) error(bps_broken);
                    for (size_t i = 0; i < length; i++) write8(read8());
                }
                break;
                case SourceCopy:
                {
                    size_t encodeddistance;
                    decodeto(encodeddistance);
                    size_t distance = encodeddistance >> 1;
                    if ((encodeddistance & 1) == 0) inreadat += distance;
                    else inreadat -= distance;
                    
                    if (inreadat < instart || inreadat + length > inend) error(bps_broken);
                    for (size_t i = 0; i < length; i++) write8(*inreadat++);
                }
                break;
                case TargetCopy:
                {
                    size_t encodeddistance;
                    decodeto(encodeddistance);
                    size_t distance = encodeddistance >> 1;
                    if ((encodeddistance & 1) == 0) outreadat += distance;
                    else outreadat -= distance;
                    
                    if (outreadat < outstart || outreadat >= outat || outreadat + length > outend) error(bps_broken);
                    for (size_t i = 0; i < length; i++) write8(*outreadat++);
                }
                break;
            }
        }
        if (patchat != patchend) error(bps_broken);
        if (outat != outend) error(bps_broken);
        
        uint32_t crc_out_a = crc32(out->ptr, out->len);
        
        if (crc_out_a != crc_out_e)
        {
            error_code = bps_not_this;
            if (!accept_wrong_input) goto exit;
        }
        return error_code;
#undef read8
#undef decodeto
#undef write8
    }
    
exit:
    free(out->ptr);
    out->len = 0;
    out->ptr = NULL;
    if (metadata)
    {
        free(metadata->ptr);
        metadata->len = 0;
        metadata->ptr = NULL;
    }
    return error_code;
}
